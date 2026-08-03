package org.j2os.platform.jsecurity.access;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A JVM-wide registry of field-level and whole-action access restrictions, keyed by an
 * arbitrary {@code scope} (e.g. a role or tenant), a target class, and an action name.
 * <p>
 * Two things can be registered per {@code scope + class + action}, independently of each
 * other: individual restricted fields (see {@link #registerFieldLimitation}), whose values get
 * nulled out (or restored from an old copy) by {@link #apply}; and/or a full denial (see
 * {@link #registerActionDenial}), which makes {@link #apply} throw {@link DeniedException}
 * outright. Registering or unregistering one does not affect the other - e.g. removing a
 * denial via {@link #unregisterActionDenial} leaves any field limitations registered for the
 * same key untouched, and vice versa.
 * <p>
 * Typical usage: register restrictions once (e.g. at startup or whenever roles/permissions
 * change), then call {@link #apply(String, Object, String)} — or the old-value-aware
 * {@link #apply(String, Object, Object, String)} overload — before returning or persisting an
 * object, to get back a restricted copy.
 * <p>
 * <b>Thread-safety.</b> Unlike the per-request builder classes in {@code org.j2os.platform.page2},
 * this registry is intentionally a shared, global, JVM-wide store ({@link ConcurrentHashMap}/
 * {@link CopyOnWriteArraySet}), since registrations are expected to be read concurrently by many
 * requests and may also be registered/unregistered concurrently at runtime (e.g. two admins
 * granting/revoking access at the same time).
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>A restricted field's declared type must be a wrapper type (e.g. {@code Integer}, not
 *       {@code int}) so it can be nulled out by {@link #apply}. This is enforced by {@link
 *       #registerFieldLimitation(String, Class, String, String)}, but only best-effort by the
 *       {@code String className} overload: if the named class cannot be loaded via {@link
 *       Class#forName(String)} at registration time, validation is silently skipped (a warning
 *       is logged), and a primitive-typed field registered this way would only surface a
 *       problem later, when {@link #apply} tries to null it out via reflection.</li>
 *   <li>Field path validation is performed against each field's <em>declared</em> type, while
 *       {@link #apply} reads/writes fields by reflecting on the target's actual <em>runtime</em>
 *       type. For fields declared with an abstract/interface type, a runtime subtype that
 *       doesn't declare the same field the same way can produce a confusing failure at apply
 *       time despite having passed validation at registration time.</li>
 *   <li>{@link #apply} performs a <b>shallow</b> copy of the target: only the object itself,
 *       and any nested containers that lie directly on a restricted field's dotted path, are
 *       copied. Any other nested/mutable field not on a restricted path is shared by reference
 *       with the original — mutating it through the returned copy also mutates the original.</li>
 *   <li>Every class this registry ever copies must have a no-arg constructor (may be
 *       non-public); classes without one throw a {@link RuntimeException} the first time
 *       {@link #apply} is called on an instance of that class.</li>
 *   <li>If a full action denial is registered for a {@code scope + class + action}, {@link
 *       #apply} throws {@link DeniedException} immediately, before copying the target or
 *       looking at any per-field restrictions also registered for that same key.</li>
 *   <li>The {@link #apply(String, Object, Object, String)} overload requires {@code oldTarget}
 *       (when non-null) to be the exact same runtime class as {@code target}; a mismatch throws
 *       {@link IllegalArgumentException} up front rather than failing deep inside reflection.</li>
 *   <li>The class-keyed reflection caches ({@link #FIELD_CACHE}, {@link #COPYABLE_FIELDS_CACHE},
 *       {@link #CONSTRUCTOR_CACHE}) use {@link ClassValue} rather than a {@code Map<Class<?>, ...>},
 *       so cached data for a class does not outlive the class itself even though that data
 *       (cached {@link Field}/{@link Constructor} objects) references the class right back - this
 *       avoids pinning classloaders (e.g. on hot-redeploy in a servlet container). {@link
 *       #FIELD_PATH_SEGMENTS_CACHE} remains an unbounded, non-weak cache, since it is keyed by
 *       field-path strings rather than by {@link Class} and so carries no classloader-leak risk;
 *       its size is bounded by how many distinct field paths are ever registered or looked up.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class RequestAccessControl {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestAccessControl.class);

    /** Registered restrictions, keyed by {@code scope|className|action} (each component escaped - see {@link #key}). */
    private static final Map<String, Restriction> LIMITATIONS = new ConcurrentHashMap<>();

    /**
     * Resolved {@link Field} objects, cached per declaring class, including negative
     * (not-found) lookups via {@link #NO_FIELD_SENTINEL}. Uses {@link ClassValue} rather than a
     * {@code Map<Class<?>, ...>} (even a weak-keyed one): the cached {@link Field} objects here
     * strongly reference their {@link Field#getDeclaringClass() declaring class} - i.e. the same
     * class used as the cache key - and a weak-keyed map whose value holds a strong path back to
     * its own key can never actually be collected (the map's always-reachable {@code Entry}
     * provides an external strong root back to the "weakly" held class). {@link ClassValue}
     * associates data with the class itself rather than via such an external entry, so a class
     * that becomes otherwise unreachable is collected together with its cached data, cycle and
     * all, instead of being pinned for the life of the JVM.
     */
    private static final ClassValue<Map<String, Field>> FIELD_CACHE = new ClassValue<Map<String, Field>>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    /**
     * Every non-static instance field of a class eligible for shallow-copying, cached by class.
     * Uses {@link ClassValue} for the same reason as {@link #FIELD_CACHE} - the cached {@link
     * Field} objects reference their declaring class strongly.
     */
    private static final ClassValue<List<Field>> COPYABLE_FIELDS_CACHE = new ClassValue<List<Field>>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> allFields = FieldUtils.getAllFieldsList(type);
            List<Field> instanceFields = new ArrayList<>(allFields.size());
            for (Field field : allFields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    requireAccessible(field, type);
                    instanceFields.add(field);
                }
            }
            return instanceFields;
        }
    };

    /**
     * The accessible no-arg constructor of a class, cached by class, used by {@link
     * #shallowCopy}. Uses {@link ClassValue} for the same reason as {@link #FIELD_CACHE} - the
     * cached {@link Constructor} references its declaring class strongly.
     */
    private static final ClassValue<Constructor<?>> CONSTRUCTOR_CACHE = new ClassValue<Constructor<?>>() {
        @Override
        protected Constructor<?> computeValue(Class<?> type) {
            if (type.isRecord()) {
                throw new RuntimeException(
                        type.getName() + " is a record and cannot be used with RequestAccessControl - "
                                + "records have no no-arg constructor (their canonical constructor requires "
                                + "all component values) and their fields cannot be reassigned after "
                                + "construction, so apply()'s copy-then-null-out-restricted-fields approach "
                                + "does not apply to them");
            }
            try {
                Constructor<?> declaredConstructor = type.getDeclaredConstructor();
                requireAccessible(declaredConstructor, type);
                return declaredConstructor;
            } catch (NoSuchMethodException noSuchMethodException) {
                throw new RuntimeException(
                        type.getName() + " must have a no-arg constructor to be used with RequestAccessControl",
                        noSuchMethodException);
            }
        }
    };

    /**
     * A dotted field path split into its individual segments, cached by the original path
     * string. Keyed by field-path strings rather than by {@link Class}, so entries here do not
     * pin any classloader; left as an unbounded {@link ConcurrentHashMap} since its only growth
     * risk is the number of distinct field-path strings ever registered or looked up, which is
     * bounded by how many restrictions are configured, not by request volume.
     */
    private static final Map<String, String[]> FIELD_PATH_SEGMENTS_CACHE = new ConcurrentHashMap<>();

    /** Sentinel {@link Field} instance used in {@link #FIELD_CACHE} to represent a cached "field not found" result. */
    private static final Field NO_FIELD_SENTINEL = resolveNoFieldSentinel();

    private RequestAccessControl() {
    }

    /**
     * Resolves the sentinel field used to cache negative field lookups.
     *
     * @return the {@link FieldCacheMarker#noField} field
     * @throws ExceptionInInitializerError if the sentinel field cannot be resolved (should never happen)
     */
    private static Field resolveNoFieldSentinel() {
        try {
            return FieldCacheMarker.class.getDeclaredField("noField");
        } catch (NoSuchFieldException noSuchFieldException) {
            throw new ExceptionInInitializerError(noSuchFieldException);
        }
    }

    /**
     * Builds the registry key for a scope/class/action combination.
     * <p>
     * Each component is escaped before joining (backslash doubled, then {@code |} escaped to
     * {@code \|}) so that a {@code |} character occurring naturally inside {@code scope} or
     * {@code action} (unlike {@code className}, these are not guaranteed to be {@code |}-free)
     * cannot make two different scope/class/action triples collide on the same registry key.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified target class name
     * @param action    the action name
     * @return the composite registry key
     */
    private static String key(String scope, String className, String action) {
        return escapeKeyComponent(scope) + '|' + escapeKeyComponent(className) + '|' + escapeKeyComponent(action);
    }

    /**
     * Escapes a single registry-key component so it can be safely joined with {@code |} by
     * {@link #key}: every {@code \} is doubled, then every {@code |} is prefixed with a
     * {@code \}. This makes the escaped-and-joined key unambiguous - the unescaped component
     * boundaries can always be recovered - even if the raw component itself contains {@code |}
     * or {@code \} characters.
     *
     * @param value the raw key component to escape
     * @return the escaped component
     */
    private static String escapeKeyComponent(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }

    /**
     * Splits (and caches) a dotted field path into its individual segments.
     *
     * @param fieldPath the dotted field path, e.g. {@code "car.factory.name"}
     * @return the path's segments, in order
     */
    private static String[] pathSegmentsOf(String fieldPath) {
        return FIELD_PATH_SEGMENTS_CACHE.computeIfAbsent(fieldPath, path -> path.split("\\."));
    }

    // ==================== Register ====================

    /**
     * Registers a field-level restriction for the given scope/class/action, validating the
     * field path against the class up front.
     * <p>
     * Independent of any full action denial registered for the same key (see {@link
     * #registerActionDenial}) - both can be registered at once, and {@link #apply} always
     * honors the denial first regardless of registration order.
     *
     * @param scope  the restriction scope
     * @param clazz  the target class the restriction applies to
     * @param field  the (possibly dotted, nested) field path to restrict
     * @param action the action this restriction applies to
     * @throws IllegalArgumentException if the field path does not resolve on {@code clazz}, or
     *                                   its leaf field is a primitive type
     * @throws NullPointerException     if {@code field} is {@code null}
     */
    public static void registerFieldLimitation(String scope, Class<?> clazz, String field, String action) {
        validateFieldPath(clazz, field);
        registerFieldLimitationInternal(scope, clazz.getName(), field, action);
    }

    /**
     * Registers a field-level restriction for the given scope/class-name/action.
     * <p>
     * Field path validation is attempted (see {@link #tryValidateFieldPath}) but is only
     * best-effort: if {@code className} cannot be resolved via {@link Class#forName(String)}
     * at this point, the registration proceeds unvalidated and a warning is logged.
     * <p>
     * Independent of any full action denial registered for the same key - see {@link
     * #registerFieldLimitation(String, Class, String, String)}.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified name of the target class the restriction applies to
     * @param field     the (possibly dotted, nested) field path to restrict
     * @param action    the action this restriction applies to
     * @throws NullPointerException if {@code field} is {@code null}
     */
    public static void registerFieldLimitation(String scope, String className, String field, String action) {
        Objects.requireNonNull(field, "field must not be null");
        tryValidateFieldPath(className, field);
        registerFieldLimitationInternal(scope, className, field, action);
    }

    /**
     * Shared implementation of both {@code registerFieldLimitation} overloads, with no
     * validation of its own - each overload is responsible for validating the field path
     * (strictly, or best-effort) exactly once before calling this.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified name of the target class the restriction applies to
     * @param field     the (possibly dotted, nested) field path to restrict
     * @param action    the action this restriction applies to
     */
    private static void registerFieldLimitationInternal(String scope, String className, String field, String action) {
        LIMITATIONS.compute(key(scope, className, action), (limitationKey, restriction) -> {
            Restriction updated = restriction != null ? restriction : new Restriction();
            updated.restrictedFields.add(field);
            return updated;
        });
    }

    /**
     * Attempts to validate a field path against a class named by string, logging a warning
     * (and doing nothing else) if the class cannot be resolved or the path is invalid.
     *
     * @param className the fully qualified class name to try to resolve
     * @param fieldPath the field path to validate against it, if resolved
     */
    private static void tryValidateFieldPath(String className, String fieldPath) {
        try {
            validateFieldPath(Class.forName(className), fieldPath);
        } catch (ClassNotFoundException classNotFoundException) {
            LOGGER.warn("RequestAccessControl: could not validate field path '{}' for class '{}' at "
                            + "registration time (class not resolvable via Class.forName) - typos or "
                            + "primitive-typed fields on this path won't be caught until apply() is called",
                    fieldPath, className);
        } catch (IllegalArgumentException illegalArgumentException) {
            LOGGER.warn("RequestAccessControl: registered an invalid field path '{}' for class '{}': {}",
                    fieldPath, className, illegalArgumentException.getMessage());
        }
    }

    /**
     * Registers a full denial of the given action, for the given scope and class.
     * <p>
     * Independent of any field limitations registered for the same key (see {@link
     * #registerFieldLimitation(String, Class, String, String)}) - both can be registered at
     * once, and {@link #apply} always honors the denial first regardless of registration order.
     *
     * @param scope  the restriction scope
     * @param clazz  the target class the denial applies to
     * @param action the action to deny outright
     */
    public static void registerActionDenial(String scope, Class<?> clazz, String action) {
        registerActionDenial(scope, clazz.getName(), action);
    }

    /**
     * Registers a full denial of the given action, for the given scope and class name.
     * <p>
     * Independent of any field limitations registered for the same key - see {@link
     * #registerActionDenial(String, Class, String)}.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified name of the target class the denial applies to
     * @param action    the action to deny outright
     */
    public static void registerActionDenial(String scope, String className, String action) {
        LIMITATIONS.compute(key(scope, className, action), (limitationKey, restriction) -> {
            Restriction updated = restriction != null ? restriction : new Restriction();
            updated.denied = true;
            return updated;
        });
    }

    // ==================== Unregister ====================

    /**
     * Removes a previously registered field restriction. If there is nothing to remove, this
     * is silently ignored. Does not affect any full action denial registered for the same key.
     *
     * @param scope  the restriction scope
     * @param clazz  the target class the restriction was registered against
     * @param field  the field path to stop restricting
     * @param action the action the restriction was registered for
     */
    public static void unregisterFieldLimitation(String scope, Class<?> clazz, String field, String action) {
        unregisterFieldLimitation(scope, clazz.getName(), field, action);
    }

    /**
     * Removes a previously registered field restriction, by class name. If there is nothing to
     * remove, this is silently ignored. Does not affect any full action denial registered for
     * the same key. If removing this field leaves the key with no denial and no restricted
     * fields, the key itself is removed from the registry.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified name of the target class the restriction was registered against
     * @param field     the field path to stop restricting
     * @param action    the action the restriction was registered for
     */
    public static void unregisterFieldLimitation(String scope, String className, String field, String action) {
        LIMITATIONS.computeIfPresent(key(scope, className, action), (limitationKey, restriction) -> {
            restriction.restrictedFields.remove(field);
            return restriction.isEmpty() ? null : restriction;
        });
    }

    /**
     * Removes a previously registered full action denial (with no specific field), registered
     * via {@link #registerActionDenial}. Does not affect any field limitations registered for
     * the same key.
     *
     * @param scope  the restriction scope
     * @param clazz  the target class the denial was registered against
     * @param action the action the denial was registered for
     */
    public static void unregisterActionDenial(String scope, Class<?> clazz, String action) {
        unregisterActionDenial(scope, clazz.getName(), action);
    }

    /**
     * Removes a previously registered full action denial (with no specific field), by class name.
     * Does not affect any field limitations registered for the same key. If removing this
     * denial leaves the key with no denial and no restricted fields, the key itself is removed
     * from the registry.
     *
     * @param scope     the restriction scope
     * @param className the fully qualified name of the target class the denial was registered against
     * @param action    the action the denial was registered for
     */
    public static void unregisterActionDenial(String scope, String className, String action) {
        LIMITATIONS.computeIfPresent(key(scope, className, action), (limitationKey, restriction) -> {
            restriction.denied = false;
            return restriction.isEmpty() ? null : restriction;
        });
    }

    // ==================== Apply ====================

    /**
     * Applies the registered restrictions to a shallow copy of {@code target}; restricted
     * fields are set to {@code null}.
     *
     * @param scope  the restriction scope to look restrictions up under
     * @param target the object to restrict; if {@code null}, {@code null} is returned as-is
     * @param action the action being performed, used to look up restrictions
     * @param <T>    the target's type
     * @return a restricted shallow copy of {@code target}, or {@code null} if {@code target} was {@code null}
     * @throws DeniedException if a full denial is registered for this scope/class/action
     */
    public static <T> T apply(String scope, T target, String action) {
        return applyInternal(scope, target, null, false, action);
    }

    /**
     * Applies the registered restrictions to a shallow copy of {@code target}; restricted
     * fields fall back to {@code oldTarget}'s corresponding value (which may itself be
     * {@code null}) instead of being unconditionally nulled out.
     *
     * @param scope     the restriction scope to look restrictions up under
     * @param target    the object to restrict; if {@code null}, {@code null} is returned as-is
     * @param oldTarget the previous version of the object, whose field values restricted fields fall back to
     * @param action    the action being performed, used to look up restrictions
     * @param <T>       the target's type
     * @return a restricted shallow copy of {@code target}, or {@code null} if {@code target} was {@code null}
     * @throws DeniedException         if a full denial is registered for this scope/class/action
     * @throws IllegalArgumentException if {@code oldTarget} is non-null and is not an instance of
     *                                   {@code target}'s exact runtime class
     */
    public static <T> T apply(String scope, T target, T oldTarget, String action) {
        return applyInternal(scope, target, oldTarget, true, action);
    }

    /**
     * Shared implementation of both {@code apply} overloads.
     *
     * @param scope     the restriction scope to look restrictions up under
     * @param target    the object to restrict, or {@code null}
     * @param oldTarget the previous version of the object to fall back to, or {@code null} if {@code hasOld} is false
     * @param hasOld    whether {@code oldTarget} should be used as a fallback (the two-arg overload passes false)
     * @param action    the action being performed, used to look up restrictions
     * @param <T>       the target's type
     * @return a restricted shallow copy of {@code target}, or {@code null} if {@code target} was {@code null}
     * @throws DeniedException if a full denial is registered for this scope/class/action
     */
    private static <T> T applyInternal(String scope, T target, T oldTarget, boolean hasOld, String action) {
        if (target == null) {
            return null;
        }
        if (hasOld && oldTarget != null && !target.getClass().equals(oldTarget.getClass())) {
            throw new IllegalArgumentException(
                    "target (" + target.getClass().getName() + ") and oldTarget ("
                            + oldTarget.getClass().getName() + ") must be the same runtime class");
        }

        String className = target.getClass().getName();
        Restriction restriction = LIMITATIONS.get(key(scope, className, action));

        if (restriction != null && restriction.denied) {
            throw new DeniedException(action);
        }

        T copy = shallowCopy(target);

        if (restriction == null || restriction.restrictedFields.isEmpty()) {
            return copy;
        }

        for (String fieldPath : restriction.restrictedFields) {
            applyFieldRestriction(copy, oldTarget, hasOld, fieldPath);
        }

        return copy;
    }

    /**
     * Applies a single restricted field path to a copy, shallow-copying any intermediate nested
     * containers along the path so the original object's nested objects are not mutated.
     *
     * @param copy      the (already top-level-copied) object to restrict a field on
     * @param oldTarget the previous version of the object to fall back to, if {@code hasOld} is true
     * @param hasOld    whether {@code oldTarget} should be used as a fallback instead of {@code null}
     * @param fieldPath the (possibly dotted, nested) field path to restrict
     */
    private static void applyFieldRestriction(Object copy, Object oldTarget, boolean hasOld, String fieldPath) {
        String[] pathSegments = pathSegmentsOf(fieldPath);
        Object container = copy;

        for (int segmentIndex = 0; segmentIndex < pathSegments.length - 1; segmentIndex++) {
            Object nestedValue = readField(container, pathSegments[segmentIndex]);
            if (nestedValue == null) {
                return;
            }
            Object nestedCopy = shallowCopy(nestedValue);
            writeField(container, pathSegments[segmentIndex], nestedCopy);
            container = nestedCopy;
        }

        String leafFieldName = pathSegments[pathSegments.length - 1];
        Object restoredValue = hasOld ? readFieldPath(oldTarget, pathSegments) : null;
        writeField(container, leafFieldName, restoredValue);
    }

    /**
     * Reads a dotted field path's value from an object, following each segment in turn.
     *
     * @param root         the object to start reading from
     * @param pathSegments the path's individual segments, in order
     * @return the resolved value, or {@code null} if {@code root} is null or any intermediate value is null
     */
    private static Object readFieldPath(Object root, String[] pathSegments) {
        Object current = root;
        for (String segment : pathSegments) {
            if (current == null) {
                return null;
            }
            current = readField(current, segment);
        }
        return current;
    }

    // ==================== Reflection helpers (cached) ====================

    /**
     * Resolves (and caches, including negative results) the {@link Field} for a given class and
     * field name, made accessible.
     *
     * @param clazz     the class to look the field up on
     * @param fieldName the field name to resolve
     * @return the resolved, accessible field, or {@code null} if no such field exists
     */
    private static Field resolveField(Class<?> clazz, String fieldName) {
        Map<String, Field> classFieldCache = FIELD_CACHE.get(clazz);
        Field cachedField = classFieldCache.computeIfAbsent(fieldName, name -> {
            Field field = FieldUtils.getField(clazz, name, true);
            if (field == null) {
                return NO_FIELD_SENTINEL;
            }
            requireAccessible(field, clazz);
            return field;
        });
        return cachedField == NO_FIELD_SENTINEL ? null : cachedField;
    }

    /**
     * Makes a {@link Field} or {@link Constructor} accessible via reflection, failing fast with
     * a clear message if the JVM's module system refuses (e.g. the declaring class's module is
     * not open to this class's module), rather than leaving it inaccessible and only surfacing a
     * confusing {@link IllegalAccessException} later when the member is actually read/written/
     * invoked.
     *
     * @param member the field or constructor to make accessible
     * @param clazz  the class the member belongs to, used only for the error message
     * @throws IllegalStateException if the member could not be made accessible
     */
    private static void requireAccessible(AccessibleObject member, Class<?> clazz) {
        if (!member.trySetAccessible()) {
            throw new IllegalStateException(
                    "Unable to make '" + member + "' on " + clazz.getName() + " accessible via reflection - "
                            + "its module may not be open to " + RequestAccessControl.class.getModule()
                            + "; add an 'opens " + clazz.getPackageName() + "' directive to that module,"
                            + " or run with --add-opens if this is not a modular application");
        }
    }

    /**
     * Validates that every segment of a dotted field path resolves to a real field on the
     * appropriate class along the way, and that the leaf field's type is a wrapper type (not
     * a primitive), since {@link #apply} may need to null it out.
     *
     * @param clazz     the class to start validating the path from
     * @param fieldPath the dotted field path to validate
     * @throws IllegalArgumentException if any segment does not resolve, or if the leaf field is a primitive type
     * @throws NullPointerException     if {@code fieldPath} is {@code null}
     */
    private static void validateFieldPath(Class<?> clazz, String fieldPath) {
        Objects.requireNonNull(fieldPath, "field must not be null");
        if (fieldPath.isBlank()) {
            throw new IllegalArgumentException("field path must not be blank");
        }
        Class<?> currentClass = clazz;
        Field leafField = null;
        for (String segment : fieldPath.split("\\.")) {
            Field field = resolveField(currentClass, segment);
            if (field == null) {
                throw new IllegalArgumentException(
                        "Field '" + segment + "' not found on " + currentClass.getName()
                                + " while validating path '" + fieldPath + "'");
            }
            leafField = field;
            currentClass = field.getType();
        }
        if (leafField.getType().isPrimitive()) {
            throw new IllegalArgumentException(
                    "Field '" + leafField.getName() + "' on " + leafField.getDeclaringClass().getName()
                            + " must be a Wrapper type (e.g. Integer, not int) to be used with"
                            + " RequestAccessControl - apply() nulls out restricted fields");
        }
    }

    /**
     * Reads a single field's value from an object via reflection.
     *
     * @param target    the object to read from
     * @param fieldName the field name to read
     * @return the field's current value
     * @throws RuntimeException if no such field exists, or if reading it fails
     */
    private static Object readField(Object target, String fieldName) {
        Field field = resolveField(target.getClass(), fieldName);
        if (field == null) {
            throw new RuntimeException("Field '" + fieldName + "' not found on runtime class "
                    + target.getClass() + " - if this field was validated against a declared type"
                    + " (e.g. an interface or abstract class) at registration time, the actual runtime"
                    + " type may not declare it the same way");
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException illegalAccessException) {
            throw new RuntimeException(
                    "Unable to read field '" + fieldName + "' on " + target.getClass(), illegalAccessException);
        }
    }

    /**
     * Writes a single field's value on an object via reflection.
     *
     * @param target    the object to write to
     * @param fieldName the field name to write
     * @param value     the value to assign
     * @throws RuntimeException if no such field exists, or if writing it fails
     */
    private static void writeField(Object target, String fieldName, Object value) {
        Field field = resolveField(target.getClass(), fieldName);
        if (field == null) {
            throw new RuntimeException("Field '" + fieldName + "' not found on runtime class "
                    + target.getClass() + " - if this field was validated against a declared type"
                    + " (e.g. an interface or abstract class) at registration time, the actual runtime"
                    + " type may not declare it the same way");
        }
        try {
            field.set(target, value);
        } catch (IllegalAccessException illegalAccessException) {
            throw new RuntimeException(
                    "Unable to set field '" + fieldName + "' on " + target.getClass(), illegalAccessException);
        }
    }

    /**
     * Creates a new instance of {@code target}'s runtime class and copies every non-static
     * instance field's value onto it (a shallow copy: nested mutable objects are shared by
     * reference with {@code target}, not themselves copied).
     *
     * @param target the object to copy
     * @param <T>    the object's type
     * @return a new shallow copy of {@code target}
     * @throws RuntimeException if the class has no accessible no-arg constructor, or if copying any field fails
     */
    @SuppressWarnings("unchecked")
    private static <T> T shallowCopy(T target) {
        Class<?> targetClass = target.getClass();
        T copy = (T) newInstance(targetClass);

        for (Field field : copyableFields(targetClass)) {
            try {
                field.set(copy, field.get(target));
            } catch (IllegalAccessException illegalAccessException) {
                throw new RuntimeException(
                        "Unable to copy field '" + field.getName() + "' on " + targetClass, illegalAccessException);
            }
        }

        return copy;
    }

    /**
     * Instantiates a class via its (cached) no-arg constructor, made accessible even if it is
     * not public.
     *
     * @param clazz the class to instantiate
     * @return a new instance of {@code clazz}
     * @throws RuntimeException if the class has no no-arg constructor, or if instantiation fails
     */
    private static Object newInstance(Class<?> clazz) {
        Constructor<?> constructor = CONSTRUCTOR_CACHE.get(clazz);
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException("Unable to instantiate " + clazz.getName(), reflectiveOperationException);
        }
    }

    /**
     * Returns (and caches) every non-static instance field of a class, made accessible, eligible
     * for shallow-copying by {@link #shallowCopy}.
     *
     * @param clazz the class to collect fields from
     * @return every non-static instance field declared on {@code clazz} or any of its superclasses
     */
    private static List<Field> copyableFields(Class<?> clazz) {
        return COPYABLE_FIELDS_CACHE.get(clazz);
    }

    /** Private holder for the {@link #NO_FIELD_SENTINEL} field used to mark cached negative field lookups. */
    private static final class FieldCacheMarker {
        /** Never read or written; only its {@link Field} identity, as a unique sentinel, matters. */
        private static Object noField;
    }

    /**
     * A single registered restriction for one {@code scope + class + action} key. A full
     * denial ({@link #denied}) and a set of restricted field paths ({@link #restrictedFields})
     * are tracked independently, exactly mirroring the pre-existing behavior where both were
     * registered/unregistered without affecting one another, and {@link #apply} always honors
     * a denial first regardless of what field restrictions are also present.
     */
    private static final class Restriction {
        /** Whether the whole action is denied outright for this key. */
        private volatile boolean denied;

        /** The restricted field paths registered for this key, independent of {@link #denied}. */
        private final Set<String> restrictedFields = new CopyOnWriteArraySet<>();

        /**
         * @return true if this restriction carries neither a denial nor any restricted fields,
         * meaning it is safe to remove the whole key from {@link #LIMITATIONS}
         */
        private boolean isEmpty() {
            return !denied && restrictedFields.isEmpty();
        }
    }

    // ==================== Exception ====================

    /**
     * Thrown by {@link #apply} when a full denial is registered for the requested
     * scope/class/action.
     */
    public static final class DeniedException extends RuntimeException {
        /**
         * Creates a denial exception whose message is the denied action's name.
         *
         * @param action the action that was denied
         */
        public DeniedException(String action) {
            super(action);
        }
    }
}