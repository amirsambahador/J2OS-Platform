package org.j2os.platform.jvalidation;

import org.j2os.platform.jutil.date.JDate;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Fluent, reflection-free (per rule) validation builder for an arbitrary
 * target object {@code T}.
 * <p>
 * Rules are attached to a field selected via a method reference (e.g.
 * {@code User::getUsername}), and every rule records a
 * {@link ValidationResult.Error} instead of throwing when it fails, so an
 * entire object can be validated in one pass and all violations collected
 * at once - see {@link #validate()} and {@link #validateOrThrow()}.
 * <p>
 * <b>Evaluation is eager, not deferred:</b> each rule method (e.g. {@link
 * Field#required()}, {@link Field#minLength(int)}) runs and records its
 * outcome the moment it is called, as part of building the fluent chain -
 * not later, when {@link #validate()} is called. {@link #validate()} simply
 * packages up whatever errors have already been recorded into a {@link
 * ValidationResult}; calling it on a validator with no rules attached
 * returns a result with no errors, not "no rules were run yet". This eager
 * design is what lets {@link Field#message(String)}/{@link
 * Field#messageKey(String)} customize the immediately preceding rule's
 * outcome, since that rule has already executed by the time they're called.
 * <p>
 * <b>Not thread-safe:</b> a {@link Validator} is a single-use, single-threaded
 * builder - create one per validation (via {@link #of(Object)}) and drive its
 * whole fluent chain from one thread. Sharing one {@link Validator} instance's
 * chain across multiple threads is not supported.
 * <p>
 * Typical usage:
 * <pre>{@code
 * ValidationResult result = Validator.of(user)
 *         .field(User::getUsername).required().minLength(5)
 *         .field(User::getEmail).regex(Patterns.EMAIL)
 *         .validate();
 *
 * if (!result.isValid()) {
 *     result.errors().forEach(System.out::println);
 * }
 * }</pre>
 *
 * @param <T> the type of the object being validated
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class Validator<T> {

    /**
     * Caches each distinct lambda/method-reference class's accessible
     * {@code writeReplace} method, so repeated calls to {@link
     * #extractFieldName(Serializable)} only pay for the reflective
     * <em>invocation</em> of {@code writeReplace} (needed every time, since
     * it returns metadata about the specific lambda instance passed in),
     * not the (comparatively expensive) {@code getDeclaredMethod} lookup
     * that finds it.
     * <p>
     * Uses {@link ClassValue} rather than a {@code Map<Class<?>, Method>}:
     * the cached {@link Method} holds a strong reference back to its own
     * {@link Method#getDeclaringClass() declaring class} - the same class
     * used as the lookup key - so a plain map (even a weak-keyed one) would
     * never actually let that class become collectible, the same
     * classloader-pinning hazard already diagnosed and fixed this way in
     * {@code RequestAccessControl}.
     */
    private static final ClassValue<Method> WRITE_REPLACE_CACHE = new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method writeReplace = type.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                return writeReplace;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    };

    /**
     * Caches the resolved field name, keyed by the lambda's semantic
     * identity ({@code implClass#implMethodName(implMethodSignature)} from
     * its {@link SerializedLambda}) rather than by {@code lambda.getClass()}.
     * Keying on the actual referenced method - rather than on however many
     * distinct classes the JVM happens to generate for lambda call sites -
     * makes correctness independent of that (unspecified) JVM behavior.
     * Keyed by plain {@link String}s (not {@link Class}), so unlike {@link
     * #WRITE_REPLACE_CACHE} this carries no classloader-pinning risk and a
     * regular {@link ConcurrentHashMap} is fine here.
     */
    private static final Map<String, String> FIELD_NAME_CACHE = new ConcurrentHashMap<>();

    /** The object being validated. */
    private final T target;

    /** All validation errors recorded so far across every field of this validator. */
    private final List<ValidationResult.Error> errors = new ArrayList<>();

    /**
     * The condition under which subsequently attached rules are actually
     * evaluated. Defaults to "always active"; changed by {@link #when(Predicate)}
     * and reset by {@link #endWhen()}.
     */
    private Predicate<T> activeCondition = t -> true;

    /**
     * Caches {@link #activeCondition}'s result for the current {@link
     * #when(Predicate)} block, so a single expensive predicate is evaluated
     * once and reused across every {@link #field(SerializableFunction)}
     * call in that block instead of being re-run for each one. {@code null}
     * means "not yet evaluated for the current condition"; reset to
     * {@code null} whenever {@link #activeCondition} changes.
     */
    private Boolean cachedConditionResult;

    private Validator(T target) {
        this.target = target;
    }

    /**
     * Starts a new validation chain for the given target object.
     *
     * @param target the object to validate
     * @param <T>    the type of the target object
     * @return a new {@link Validator} for {@code target}
     */
    public static <T> Validator<T> of(T target) {
        return new Validator<>(target);
    }

    /**
     * Derives a human-readable field name from a getter method reference by
     * inspecting the {@link SerializedLambda} produced by its synthetic
     * {@code writeReplace} method, stripping a leading {@code get}/{@code is}
     * prefix and lower-casing the first remaining character (e.g.
     * {@code User::getUsername} -&gt; {@code "username"}).
     * <p>
     * The resolved name is cached by the lambda's semantic identity (see
     * {@link #FIELD_NAME_CACHE}), and the reflective {@code writeReplace}
     * method handle is separately cached per lambda class (see {@link
     * #WRITE_REPLACE_CACHE}); if reflection fails for any reason, {@code
     * "field"} is used as a generic fallback name instead of propagating
     * the failure.
     *
     * @param lambda the method reference to inspect; must implement {@link Serializable}
     * @return the derived field name, or {@code "field"} if it could not be determined
     */
    private static String extractFieldName(Serializable lambda) {
        try {
            Method writeReplace = WRITE_REPLACE_CACHE.get(lambda.getClass());
            SerializedLambda sl = (SerializedLambda) writeReplace.invoke(lambda);
            String cacheKey = sl.getImplClass() + '#' + sl.getImplMethodName() + sl.getImplMethodSignature();
            return FIELD_NAME_CACHE.computeIfAbsent(cacheKey, k -> resolveFieldName(sl.getImplMethodName()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "field";
        }
    }

    /**
     * Strips a leading {@code get}/{@code is} accessor prefix from a method
     * name and decapitalizes what remains (see {@link #decapitalize(String)}).
     *
     * @param methodName the implementation method name from a {@link SerializedLambda}
     * @return the derived field name
     */
    private static String resolveFieldName(String methodName) {
        String name = methodName;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        }
        if (name.isEmpty()) {
            return methodName;
        }
        return decapitalize(name);
    }

    /**
     * Lower-cases the leading character of {@code name}, following the same
     * convention as {@link java.beans.Introspector#decapitalize(String)}:
     * if the first two characters are both upper case, the name is left
     * untouched (so {@code getURL()} yields {@code "URL"}, not
     * {@code "uRL"}, matching what most frameworks - and JavaBeans property
     * naming - already expect).
     *
     * @param name a non-empty string, already stripped of its
     *             {@code get}/{@code is} accessor prefix
     * @return the decapitalized name
     */
    private static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Starts a conditional block: every rule attached after this call (until
     * {@link #endWhen()} is called) is only evaluated when {@code condition}
     * holds for the target object.
     * <p>
     * A {@code null} condition is treated as "never active" (equivalent to
     * {@code when(t -> false)}) rather than being stored as-is: since {@code
     * condition} is only invoked later, inside {@link #field(SerializableFunction)}'s
     * exception handling, storing {@code null} directly would surface as a
     * misleading {@code FIELD_ACCESS_ERROR} (attributing the failure to the
     * field access itself rather than to this call) instead of just cleanly
     * deactivating subsequent rules.
     *
     * @param condition the condition that must hold for subsequent rules to run
     * @return this validator, for chaining
     */
    public Validator<T> when(Predicate<T> condition) {
        this.activeCondition = condition != null ? condition : t -> false;
        this.cachedConditionResult = null;
        return this;
    }

    /**
     * Ends the current conditional block started by {@link #when(Predicate)};
     * every rule attached after this call always runs, regardless of any
     * earlier condition.
     *
     * @return this validator, for chaining
     */
    public Validator<T> endWhen() {
        this.activeCondition = t -> true;
        this.cachedConditionResult = null;
        return this;
    }

    /**
     * Selects a field (via a getter method reference) to attach rules to.
     * The field's active/inactive state (see {@link #when(Predicate)}) is
     * resolved once per {@code when()} block: the first {@link
     * #field(SerializableFunction)} call after {@link #when(Predicate)} (or
     * {@link #endWhen()}) evaluates the condition and caches the result;
     * later {@code field()} calls in the same block reuse that cached
     * result instead of re-invoking the condition, so an expensive
     * predicate only runs once per block rather than once per field.
     *
     * @param getter the getter method reference identifying the field
     * @param <R>    the field's value type
     * @return a {@link Field} bound to this field, for attaching rules
     */
    public <R> Field<T, R> field(SerializableFunction<T, R> getter) {
        String fieldName = extractFieldName(getter);
        try {
            if (cachedConditionResult == null) {
                cachedConditionResult = activeCondition.test(target);
            }
            boolean active = cachedConditionResult;
            R value = getter.apply(target);
            return new Field<>(this, target, value, fieldName, active);
        } catch (RuntimeException e) {
            // active=false so no further rule on this field records additional, likely-spurious errors
            Field<T, R> field = new Field<>(this, target, null, fieldName, false);
            // Recorded via field.check(...) - not addError(...) directly - so lastError is set on the
            // field, letting a chained .message()/.messageKey() customize this error like any other rule.
            field.check(false, "FIELD_ACCESS_ERROR",
                    fieldName + ": failed to evaluate field (" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
            return field;
        }
    }

    /**
     * Packages up the outcome of every rule already run during this chain's
     * construction into a {@link ValidationResult}, without throwing
     * regardless of whether any rule failed. Rules run eagerly as each is
     * attached (see the class-level "Evaluation is eager" note) - this
     * method does not itself evaluate anything; a validator with no rules
     * attached yields a result with no errors.
     *
     * @return a {@link ValidationResult} describing validity and any recorded errors
     */
    public ValidationResult validate() {
        return new ValidationResult(new ArrayList<>(errors));
    }

    /**
     * Packages up the outcome of every rule already run during this chain's
     * construction (see {@link #validate()}) and throws {@link
     * ValidationException} if at least one error was recorded. Like {@link
     * #validate()}, this method does not itself evaluate any rules - it
     * only inspects what was already recorded during the chain's
     * construction.
     *
     * @return a {@link ValidationResult} describing validity and any recorded errors
     * @throws ValidationException if the result is not {@link ValidationResult#isValid()}
     */
    public ValidationResult validateOrThrow() {
        ValidationResult result = validate();
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
        return result;
    }

    /**
     * Records a validation error against this validator's shared error list.
     * Called internally by {@link Field#check(boolean, String, String)}.
     *
     * @param error the error to record
     */
    private void addError(ValidationResult.Error error) {
        errors.add(error);
    }

    /**
     * A {@link Function} that is also {@link Serializable}, which is what
     * allows {@link #extractFieldName(Serializable)} to recover the
     * originating method name from a method reference passed to
     * {@link #field(SerializableFunction)}.
     *
     * @param <T> the input type (the object owning the field)
     * @param <R> the field's value type
     */
    @FunctionalInterface
    public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {
    }

    /**
     * Internal helper for date-related rules: parses, validates and compares
     * {@code yyyy/MM/dd} date strings in either the Gregorian or Persian
     * calendar, and caches "today" (in both calendars) for the current day
     * so repeated {@link Field#past()}/{@link Field#future()} checks don't
     * recompute it.
     */
    private static final class DateUtil {

        /** Required textual shape for a date string: exactly {@code yyyy/MM/dd}. */
        private static final Pattern FORMAT = Pattern.compile("^\\d{4}/\\d{2}/\\d{2}$");

        /** Guards the (rare) recomputation of the cached "today" values. */
        private static final Object TODAY_LOCK = new Object();

        /** Epoch day for which {@link #cachedGregorianToday}/{@link #cachedPersianToday} are valid. */
        private static volatile long cachedEpochDay = Long.MIN_VALUE;

        /** Today's date in the Gregorian calendar, as {@code {year, month, day}}, cached per day. */
        private static volatile int[] cachedGregorianToday;

        /** Today's date in the Persian calendar, as {@code {year, month, day}}, cached per day. */
        private static volatile int[] cachedPersianToday;

        private DateUtil() {
        }

        /**
         * Recomputes and caches today's date (in both calendars) if the
         * system date has changed since the last computation. Uses
         * double-checked locking so the common case (same day) is
         * lock-free.
         */
        private static void refreshTodayIfNeeded() {
            long epochDay = LocalDate.now().toEpochDay();
            if (epochDay == cachedEpochDay) {
                return;
            }
            synchronized (TODAY_LOCK) {
                if (epochDay == cachedEpochDay) {
                    return;
                }
                JDate pd = new JDate();
                int[] gregorian = {pd.getGregorianYear(), pd.getGregorianMonth(), pd.getGregorianDay()};
                int[] persian = {pd.getPersianYear(), pd.getPersianMonth(), pd.getPersianDay()};
                cachedGregorianToday = gregorian;
                cachedPersianToday = persian;
                cachedEpochDay = epochDay;
            }
        }

        /**
         * Parses a {@code yyyy/MM/dd} date string into its numeric
         * components, without validating that the date actually exists
         * (e.g. does not reject {@code 2020/02/30}).
         *
         * @param s the date string to parse
         * @return {@code {year, month, day}}, or {@code null} if {@code s} is
         *         {@code null}, not in {@code yyyy/MM/dd} shape, or contains
         *         non-numeric parts
         */
        private static int[] parseRaw(String s) {
            if (s == null || !FORMAT.matcher(s).matches()) {
                return null;
            }
            String[] parts = s.split("/");
            try {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Determines whether a Gregorian year is a leap year (standard
         * Gregorian leap-year rule).
         *
         * @param y the Gregorian year
         * @return {@code true} if {@code y} is a Gregorian leap year
         */
        private static boolean isLeapGregorian(int y) {
            return (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0);
        }

        /**
         * Validates that a string is both {@code yyyy/MM/dd}-shaped and a
         * real Gregorian calendar date (correct day-of-month for the given
         * month/year, including leap years).
         *
         * @param s the date string to validate
         * @return {@code true} if {@code s} is a valid Gregorian date
         */
        static boolean isValidGregorian(String s) {
            int[] ymd = parseRaw(s);
            if (ymd == null) return false;
            int y = ymd[0], m = ymd[1], d = ymd[2];
            if (m < 1 || m > 12 || d < 1) return false;
            int[] len = {31, isLeapGregorian(y) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            return d <= len[m - 1];
        }

        /**
         * Validates that a string is both {@code yyyy/MM/dd}-shaped and a
         * real Persian calendar date (correct day-of-month for the given
         * month/year, including Persian leap years for Esfand).
         *
         * @param s the date string to validate
         * @return {@code true} if {@code s} is a valid Persian date
         */
        static boolean isValidPersian(String s) {
            int[] ymd = parseRaw(s);
            if (ymd == null) return false;
            int y = ymd[0], m = ymd[1], d = ymd[2];
            if (m < 1 || m > 12 || d < 1) return false;
            if (m <= 6) return d <= 31;
            if (m <= 11) return d <= 30;
            return d <= (new JDate().isLeap(y) ? 30 : 29);
        }

        /**
         * Validates a date string against whichever calendar {@code kind}
         * specifies.
         *
         * @param kind the calendar to validate against
         * @param s    the date string to validate
         * @return {@code true} if {@code s} is a valid date in that calendar
         */
        static boolean isValidForKind(Kind kind, String s) {
            return kind == Kind.GREGORIAN ? isValidGregorian(s) : isValidPersian(s);
        }

        /**
         * Converts a Gregorian year/month/day into its Julian Day Number,
         * used as a calendar-independent basis for date comparisons.
         *
         * @param y the Gregorian year
         * @param m the Gregorian month (1-12)
         * @param d the Gregorian day of month
         * @return the corresponding Julian Day Number
         */
        private static long gregorianToJdn(int y, int m, int d) {
            int a = (14 - m) / 12;
            int y2 = y + 4800 - a;
            int m2 = m + 12 * a - 3;
            return d + (153L * m2 + 2) / 5 + 365L * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045;
        }

        /**
         * Converts a {@code yyyy/MM/dd} date string (in the given calendar)
         * into its Julian Day Number, so dates from either calendar can be
         * compared on a common numeric scale.
         *
         * @param kind the calendar the string {@code s} is expressed in
         * @param s    the date string to convert; assumed already validated
         * @return the Julian Day Number corresponding to {@code s}
         */
        static long toJdn(Kind kind, String s) {
            int[] ymd = parseRaw(s);
            if (kind == Kind.GREGORIAN) {
                return gregorianToJdn(ymd[0], ymd[1], ymd[2]);
            }
            JDate pd = new JDate();
            pd.setPersianDate(ymd[0], ymd[1], ymd[2]);
            return gregorianToJdn(pd.getGregorianYear(), pd.getGregorianMonth(), pd.getGregorianDay());
        }

        /**
         * Returns today's date (cached per calendar day) as {@code {y, m, d}}
         * in the given calendar. Returns a defensive copy: the cached array
         * itself must never be handed out directly, since a caller holding
         * that exact reference could mutate the shared cache for every
         * other concurrent/future caller for the rest of the day.
         *
         * @param kind the calendar to express today's date in
         * @return a fresh copy of today's date as {@code {year, month, day}}
         */
        static int[] today(Kind kind) {
            refreshTodayIfNeeded();
            return Arrays.copyOf(kind == Kind.GREGORIAN ? cachedGregorianToday : cachedPersianToday, 3);
        }

        /**
         * Computes a whole-years age between a birth date (in calendar
         * {@code kind}) and today (in the same calendar).
         *
         * @param kind      the calendar both {@code birthDate} and "today" are expressed in
         * @param birthDate the birth date string, assumed already validated
         * @return the age in complete years
         */
        static int ageInYears(Kind kind, String birthDate) {
            int[] birth = parseRaw(birthDate);
            int[] now = today(kind);
            int age = now[0] - birth[0];
            if (now[1] < birth[1] || (now[1] == birth[1] && now[2] < birth[2])) {
                age--;
            }
            return age;
        }

        /**
         * Formats a {@code {year, month, day}} triple back into a
         * {@code yyyy/MM/dd} string, zero-padded to 4/2/2 digits.
         *
         * @param ymd the date components to format
         * @return the formatted date string
         */
        private static String formatYmd(int[] ymd) {
            return String.format("%04d/%02d/%02d", ymd[0], ymd[1], ymd[2]);
        }

        /** Identifies which calendar a date string is expressed in. */
        enum Kind {GREGORIAN, PERSIAN}
    }

    /**
     * Represents a single field selected from the validated object, and
     * exposes the fluent rule methods (string, numeric, boolean, date,
     * collection, and cross-field) that can be attached to it. Each rule
     * method records a {@link ValidationResult.Error} on failure instead of
     * throwing, and returns {@code this} for further chaining.
     *
     * @param <T> the type of the object owning this field
     * @param <R> this field's value type
     */
    public static final class Field<T, R> {

        /** Accepted shape for a numeric string: optional {@code -}, digits, optional {@code .digits}. */
        private static final Pattern NUMBER_FORMAT = Pattern.compile("^-?\\d+(\\.\\d+)?$");

        /** The validator this field belongs to; rules delegate error recording to it. */
        private final Validator<T> parent;

        /** The object that owns this field. */
        private final T target;

        /** This field's current value, captured when {@link Validator#field(SerializableFunction)} was called. */
        private final R value;

        /** This field's derived name, used in default error messages and {@link ValidationResult.Error#getField()}. */
        private final String fieldName;

        /** Whether rules on this field should actually run (see {@link Validator#when(Predicate)}). */
        private final boolean active;

        /** The error recorded by the most recently evaluated rule, or {@code null} if it passed. */
        private ValidationResult.Error lastError;

        /** Which calendar this field's date rules are operating in, set by {@link #date()}/{@link #persianDate()}. */
        private DateUtil.Kind dateKind;

        /**
         * Whether a prior {@link #date()} or {@link #persianDate()} call was
         * made on this field but failed (as opposed to never having been
         * called at all). Lets {@link #hasDateContext()} avoid piling a
         * second, redundant error onto an already-reported invalid date.
         */
        private boolean dateContextFailed;

        // ───────────────────────── Internal infrastructure ─────────────────────────

        private Field(Validator<T> parent, T target, R value, String fieldName, boolean active) {
            this.parent = parent;
            this.target = target;
            this.value = value;
            this.fieldName = fieldName;
            this.active = active;
        }

        /**
         * Converts a primitive or object array into a {@link List}, via
         * reflection, so it can be treated uniformly with
         * {@link Collection}/{@link Map} by {@link #asCollection()}.
         *
         * @param array the array to convert (primitive or object array)
         * @return a new list containing the array's elements, boxed as needed
         */
        private static List<Object> primitiveOrObjectArrayToList(Object array) {
            int length = java.lang.reflect.Array.getLength(array);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(array, i));
            }
            return list;
        }

        /**
         * Records the outcome of a single rule: on failure, builds and
         * stores a {@link ValidationResult.Error}; on success, clears
         * {@link #lastError} (so a subsequent {@link #message(String)} call
         * has nothing to attach to).
         *
         * @param valid   whether the rule passed
         * @param code    the machine-readable error code to use if it failed
         * @param message the default human-readable message to use if it failed
         * @return this field, for chaining
         */
        private Field<T, R> check(boolean valid, String code, String message) {
            if (!valid) {
                lastError = new ValidationResult.Error(fieldName, code, message, value);
                parent.addError(lastError);
            } else {
                lastError = null;
            }
            return this;
        }

        /**
         * Short-circuits a rule when this field is inactive (see
         * {@link Validator#when(Predicate)}): clears {@link #lastError} and
         * signals the caller to skip the rule's actual logic.
         *
         * @return {@code true} if the rule should be skipped
         */
        private boolean skipIfInactive() {
            if (!active) {
                lastError = null;
                return true;
            }
            return false;
        }

        /**
         * Returns this field's value as a string via {@link Object#toString()}.
         *
         * @return the string form of the value, or {@code null} if the value is {@code null}
         */
        private String asString() {
            return value == null ? null : value.toString();
        }

        /**
         * Attempts to interpret this field's value as a {@link BigDecimal},
         * for use by the numeric rules.
         *
         * @return the value as a {@link BigDecimal}, or {@code null} if the
         *         value is {@code null} or not numeric/parseable
         */
        private BigDecimal asNumber() {
            if (value == null) {
                return null;
            }
            try {
                if (value instanceof BigDecimal bd) return bd;
                if (value instanceof Number n) return new BigDecimal(n.toString());
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // ───────────────────────── Flow control ─────────────────────────

        /**
         * Views this field's value as a {@link Collection}, for use by the
         * collection-related rules. {@link Map} values are viewed via their
         * {@link Map#entrySet()}, and arrays (primitive or object) are
         * converted to a {@link List}.
         * <p>
         * Mirrors {@link #asNumber()}'s graceful-failure pattern: instead of
         * throwing when the value isn't collection-like, this returns
         * {@code null} so callers can fold that case into a normal
         * {@code check(...)} call and record a validation error rather than
         * letting an exception escape the validation chain.
         *
         * @return this field's value as a {@link Collection}, or {@code null}
         *         if the value is non-null and not a {@link Collection},
         *         {@link Map}, or array
         */
        @SuppressWarnings("unchecked")
        private Collection<Object> asCollection() {
            if (value instanceof Collection<?> c) return (Collection<Object>) c;
            if (value instanceof Map<?, ?> m) return (Collection<Object>) (Collection<?>) m.entrySet();
            if (value != null && value.getClass().isArray()) return primitiveOrObjectArrayToList(value);
            return null;
        }

        /**
         * Selects another field (via a getter method reference) to attach
         * further rules to, continuing the same validation chain.
         *
         * @param getter the getter method reference identifying the field
         * @param <R2>   the other field's value type
         * @return a {@link Field} bound to the other field
         */
        public <R2> Field<T, R2> field(SerializableFunction<T, R2> getter) {
            return parent.field(getter);
        }

        /**
         * Starts a conditional block on the parent validator; see
         * {@link Validator#when(Predicate)}.
         *
         * @param condition the condition that must hold for subsequent rules to run
         * @return the parent validator, for chaining
         */
        public Validator<T> when(Predicate<T> condition) {
            return parent.when(condition);
        }

        /**
         * Ends the current conditional block on the parent validator; see
         * {@link Validator#endWhen()}.
         *
         * @return the parent validator, for chaining
         */
        public Validator<T> endWhen() {
            return parent.endWhen();
        }

        /**
         * Delegates to the parent validator's {@link Validator#validate()}.
         *
         * @return a {@link ValidationResult} describing validity and any recorded errors
         */
        public ValidationResult validate() {
            return parent.validate();
        }

        /**
         * Delegates to the parent validator's {@link Validator#validateOrThrow()}.
         *
         * @return a {@link ValidationResult} describing validity and any recorded errors
         * @throws ValidationException if the result is not {@link ValidationResult#isValid()}
         */
        public ValidationResult validateOrThrow() {
            return parent.validateOrThrow();
        }

        /**
         * Replaces the message text of the most recently failed rule on
         * this field with a custom message. Has no effect if the most
         * recent rule passed (there is no error to attach to).
         *
         * @param customMessage the replacement message text
         * @return this field, for chaining
         */
        public Field<T, R> message(String customMessage) {
            if (lastError != null) {
                lastError.setMessage(customMessage);
            }
            return this;
        }

        /**
         * Sets a message key (for localization) on the most recently failed
         * rule on this field. Has no effect if the most recent rule passed.
         *
         * @param key the localization key to attach
         * @return this field, for chaining
         */
        public Field<T, R> messageKey(String key) {
            if (lastError != null) {
                lastError.setMessageKey(key);
            }
            return this;
        }

        // ───────────────────────── String ─────────────────────────

        /**
         * Runs a custom, user-supplied rule with access to both the whole
         * target object and this field's value (an extension point for any
         * logic the framework does not provide out of the box - e.g.
         * calling an external virus scanner):
         * <pre>{@code
         * .field(Upload::getFile).withMethod((u, f) -> myClamAvClient.isClean(f),
         *         "VIRUS_UNSAFE", "The uploaded file was flagged as infected")
         * }</pre>
         * <p>
         * Unlike {@link #required()}, this rule does not special-case
         * {@code null} - {@code rule} is invoked with whatever value the
         * field holds (including {@code null}), so the predicate itself is
         * responsible for deciding whether a missing value is acceptable.
         * <p>
         * If {@code rule} itself throws, that exception is caught and
         * recorded as a normal validation error (using {@code code}) rather
         * than propagating out of {@link #validate()} - consistent with
         * this framework's "never throw, record an error" contract.
         *
         * @param rule    the custom predicate; receives the target object and this field's value
         * @param code    the error code to record if {@code rule} returns {@code false} or throws
         * @param message the error message to record if {@code rule} returns {@code false} or throws
         * @return this field, for chaining
         */
        public Field<T, R> withMethod(BiPredicate<T, R> rule, String code, String message) {
            if (skipIfInactive()) return this;
            try {
                return check(rule.test(target, value), code, message);
            } catch (RuntimeException e) {
                return check(false, code, message + " (rule threw " + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
            }
        }

        /**
         * The field must not be {@code null} or an empty string.
         *
         * @return this field, for chaining
         */
        public Field<T, R> required() {
            if (skipIfInactive()) return this;
            boolean ok = value != null && !(value instanceof String s && s.isEmpty());
            return check(ok, "REQUIRED", fieldName + " is required");
        }

        /**
         * The string must not be {@code null}, empty, or whitespace-only
         * (the strictest form of "required").
         *
         * @return this field, for chaining
         */
        public Field<T, R> notBlank() {
            if (skipIfInactive()) return this;
            boolean ok = value != null && !asString().isBlank();
            return check(ok, "NOT_BLANK", fieldName + " must not be blank");
        }

        /**
         * The string must have exactly the given length.
         *
         * @param len the required exact length
         * @return this field, for chaining
         */
        public Field<T, R> length(int len) {
            if (skipIfInactive()) return this;
            return check(value != null && asString().length() == len, "LENGTH",
                    fieldName + " must have length " + len);
        }

        /**
         * The string must have at least the given length.
         *
         * @param min the minimum allowed length
         * @return this field, for chaining
         */
        public Field<T, R> minLength(int min) {
            if (skipIfInactive()) return this;
            return check(value != null && asString().length() >= min, "MIN_LENGTH",
                    fieldName + " must be at least " + min + " characters");
        }

        /**
         * The string must have at most the given length.
         *
         * @param max the maximum allowed length
         * @return this field, for chaining
         */
        public Field<T, R> maxLength(int max) {
            if (skipIfInactive()) return this;
            return check(value != null && asString().length() <= max, "MAX_LENGTH",
                    fieldName + " must be at most " + max + " characters");
        }

        /**
         * The string's length must fall within {@code [min, max]}.
         *
         * @param min the minimum allowed length
         * @param max the maximum allowed length
         * @return this field, for chaining
         */
        public Field<T, R> lengthBetween(int min, int max) {
            if (skipIfInactive()) return this;
            int len = value == null ? -1 : asString().length();
            return check(len >= min && len <= max, "LENGTH_BETWEEN",
                    fieldName + " length must be between " + min + " and " + max);
        }

        /**
         * The string must match the given regular expression.
         *
         * @param pattern the regular expression, as a string
         * @return this field, for chaining
         * @implNote if {@code pattern} is {@code null} or not a syntactically
         *     valid regular expression, this is recorded as a normal {@code
         *     INVALID_PATTERN} validation error rather than letting {@link
         *     NullPointerException}/{@link PatternSyntaxException} propagate
         *     out of {@link #validate()} - consistent with this framework's
         *     "never throw, record an error" contract.
         */
        public Field<T, R> regex(String pattern) {
            if (skipIfInactive()) return this;
            if (pattern == null) {
                return check(false, "INVALID_PATTERN", fieldName + ": pattern must not be null");
            }
            boolean ok;
            try {
                ok = value != null && Pattern.matches(pattern, asString());
            } catch (PatternSyntaxException patternSyntaxException) {
                return check(false, "INVALID_PATTERN",
                        fieldName + ": '" + pattern + "' is not a valid regular expression ("
                                + patternSyntaxException.getDescription() + ")");
            }
            return check(ok, "REGEX", fieldName + " does not match pattern " + pattern);
        }

        /**
         * The string must match the given pre-compiled {@link Pattern} -
         * e.g. one of the constants in {@link Patterns}, without needing to
         * call {@code .pattern()} on it.
         *
         * @param pattern the pre-compiled pattern to match against
         * @return this field, for chaining
         * @implNote if {@code pattern} is {@code null}, this is recorded as a
         *     normal {@code INVALID_PATTERN} validation error rather than
         *     letting {@link NullPointerException} propagate out of {@link
         *     #validate()} - consistent with this framework's "never throw,
         *     record an error" contract, and with {@link #regex(String)}'s
         *     handling of a malformed pattern.
         */
        public Field<T, R> regex(Pattern pattern) {
            if (skipIfInactive()) return this;
            if (pattern == null) {
                return check(false, "INVALID_PATTERN", fieldName + ": pattern must not be null");
            }
            return check(value != null && pattern.matcher(asString()).matches(), "REGEX",
                    fieldName + " has an invalid format");
        }

        /**
         * The string, collection, map, or array must contain the given
         * value. For a {@link String} this is a substring check; for a
         * {@link Map} this checks the map's values (via
         * {@link Map#containsValue(Object)}); for a {@link Collection} or
         * array, this checks the elements (via {@link #asCollection()}).
         *
         * @param needle the value that must be present
         * @return this field, for chaining
         */
        public Field<T, R> contains(Object needle) {
            if (skipIfInactive()) return this;
            boolean ok;
            if (value instanceof String s) {
                ok = s.contains(String.valueOf(needle));
            } else if (value instanceof Map<?, ?> m) {
                ok = m.containsValue(needle);
            } else {
                Collection<Object> collection = asCollection();
                ok = collection != null && collection.contains(needle);
            }
            return check(ok, "CONTAINS", fieldName + " must contain " + needle);
        }

        /**
         * The string must start with the given prefix.
         *
         * @param prefix the required prefix
         * @return this field, for chaining
         */
        public Field<T, R> startsWith(String prefix) {
            if (skipIfInactive()) return this;
            return check(value != null && asString().startsWith(prefix), "STARTS_WITH",
                    fieldName + " must start with " + prefix);
        }

        // ───────────────────────── Numeric ─────────────────────────

        /**
         * The string must end with the given suffix.
         *
         * @param suffix the required suffix
         * @return this field, for chaining
         */
        public Field<T, R> endsWith(String suffix) {
            if (skipIfInactive()) return this;
            return check(value != null && asString().endsWith(suffix), "ENDS_WITH",
                    fieldName + " must end with " + suffix);
        }

        /**
         * Checks the string's numeric shape (e.g. {@code "123"},
         * {@code "-45.67"}) and records a real validation error - instead of
         * a raw exception - if it is invalid. After this rule, the other
         * numeric rules ({@link #min(double)}, {@link #max(double)},
         * {@link #between(double, double)}, {@link #positive()},
         * {@link #digits(int, int)}) can safely be used on the same field,
         * since they convert this same string to a number via
         * {@code toString()}.
         * <pre>{@code
         * .field(Order::getQuantityText).number().min(1).max(100)
         * }</pre>
         * <p>
         * This check is intentionally stricter than what {@link BigDecimal}
         * itself would accept: a leading {@code +}, a trailing or leading
         * {@code .} with no digit on one side (e.g. {@code "10."}, {@code
         * ".5"}), and scientific notation (e.g. {@code "1E10"}) all fail
         * this rule even though {@code new BigDecimal(...)} would parse
         * them. If a field is used with {@link #min(double)}/{@link
         * #max(double)}/etc <em>without</em> a preceding {@code number()}
         * call, those rules fall back to {@link BigDecimal}'s own (looser)
         * grammar instead.
         *
         * @return this field, for chaining
         */
        public Field<T, R> number() {
            if (skipIfInactive()) return this;
            boolean ok = value != null && NUMBER_FORMAT.matcher(asString()).matches();
            return check(ok, "NUMBER", fieldName + " must be a valid number");
        }

        /**
         * The numeric value must be at least {@code min} (inclusive). A
         * value that cannot be converted to a number also fails this rule
         * (rather than throwing).
         *
         * @param min the minimum allowed value, inclusive
         * @return this field, for chaining
         */
        public Field<T, R> min(double min) {
            if (skipIfInactive()) return this;
            BigDecimal n = asNumber();
            return check(n != null && n.compareTo(BigDecimal.valueOf(min)) >= 0, "MIN",
                    fieldName + " must be >= " + min);
        }

        /**
         * The numeric value must be at most {@code max} (inclusive). A
         * value that cannot be converted to a number also fails this rule
         * (rather than throwing).
         *
         * @param max the maximum allowed value, inclusive
         * @return this field, for chaining
         */
        public Field<T, R> max(double max) {
            if (skipIfInactive()) return this;
            BigDecimal n = asNumber();
            return check(n != null && n.compareTo(BigDecimal.valueOf(max)) <= 0, "MAX",
                    fieldName + " must be <= " + max);
        }

        /**
         * The numeric value must be positive (strictly greater than zero).
         *
         * @return this field, for chaining
         */
        public Field<T, R> positive() {
            if (skipIfInactive()) return this;
            BigDecimal n = asNumber();
            return check(n != null && n.compareTo(BigDecimal.ZERO) > 0, "POSITIVE",
                    fieldName + " must be positive");
        }

        /**
         * The numeric value must fall within {@code [min, max]}.
         *
         * @param min the minimum allowed value, inclusive
         * @param max the maximum allowed value, inclusive
         * @return this field, for chaining
         */
        public Field<T, R> between(double min, double max) {
            if (skipIfInactive()) return this;
            BigDecimal n = asNumber();
            boolean ok = n != null && n.compareTo(BigDecimal.valueOf(min)) >= 0
                    && n.compareTo(BigDecimal.valueOf(max)) <= 0;
            return check(ok, "BETWEEN", fieldName + " must be between " + min + " and " + max);
        }

        /**
         * Limits the number of integer and fraction digits of the numeric
         * value. Trailing zeros are stripped before counting, so
         * {@code 1200.00} counts as 4 integer digits and 0 fraction digits.
         * <pre>{@code
         * .field(Product::getPrice).digits(5, 3) // at most 5 integer digits, 3 fraction digits
         * }</pre>
         *
         * @param integerDigits  the maximum number of integer digits allowed
         * @param fractionDigits the maximum number of fraction digits allowed
         * @return this field, for chaining
         */
        public Field<T, R> digits(int integerDigits, int fractionDigits) {
            if (skipIfInactive()) return this;
            BigDecimal n = asNumber();
            boolean ok = false;
            if (n != null) {
                String plain = n.abs().stripTrailingZeros().toPlainString();
                int dot = plain.indexOf('.');
                String intPart = dot < 0 ? plain : plain.substring(0, dot);
                String fracPart = dot < 0 ? "" : plain.substring(dot + 1);
                int intDigits = "0".equals(intPart) ? 0 : intPart.length();
                ok = intDigits <= integerDigits && fracPart.length() <= fractionDigits;
            }
            return check(ok, "DIGITS", fieldName + " must have at most " + integerDigits
                    + " integer digit(s) and " + fractionDigits + " fraction digit(s)");
        }

        // ───────────────────────── Boolean ─────────────────────────

        /**
         * Checks the string's boolean shape - it must be exactly
         * {@code "true"} or {@code "false"} (case-insensitive) - and
         * records a validation error if it is not. After this rule,
         * {@link #isTrue()}/{@link #isFalse()} can be used on the same
         * string field.
         * <pre>{@code
         * .field(Form::getAcceptedText).bool().isTrue()
         * }</pre>
         *
         * @return this field, for chaining
         */
        public Field<T, R> bool() {
            if (skipIfInactive()) return this;
            boolean ok = value != null
                    && ("true".equalsIgnoreCase(asString()) || "false".equalsIgnoreCase(asString()));
            return check(ok, "BOOLEAN", fieldName + " must be true or false");
        }

        /**
         * The value must be {@code true} - works on both an actual
         * {@link Boolean} and the string {@code "true"} (case-insensitive).
         *
         * @return this field, for chaining
         */
        public Field<T, R> isTrue() {
            if (skipIfInactive()) return this;
            return check(value != null && "true".equalsIgnoreCase(asString()), "IS_TRUE", fieldName + " must be true");
        }

        /**
         * The value must be {@code false} - works on both an actual
         * {@link Boolean} and the string {@code "false"} (case-insensitive).
         *
         * @return this field, for chaining
         */
        public Field<T, R> isFalse() {
            if (skipIfInactive()) return this;
            return check(value != null && "false".equalsIgnoreCase(asString()), "IS_FALSE", fieldName + " must be false");
        }

        // ───────────────────────── Date (input: yyyy/MM/dd string) ─────────────────────────

        /**
         * Checks the format and validity of a Gregorian date string, and
         * records this field's calendar as Gregorian for subsequent
         * date rules ({@link #past()}, {@link #future()},
         * {@link #before(String)}, {@link #after(String)},
         * {@link #between(String, String)}, {@link #minimumAge(int)}).
         *
         * @return this field, for chaining
         */
        public Field<T, R> date() {
            if (skipIfInactive()) return this;
            boolean ok = value != null && DateUtil.isValidGregorian(asString());
            if (ok) {
                dateKind = DateUtil.Kind.GREGORIAN;
                dateContextFailed = false;
            } else {
                dateKind = null;
                dateContextFailed = true;
            }
            return check(ok, "DATE", fieldName + " must be a valid date (yyyy/MM/dd)");
        }

        /**
         * Checks the format and validity of a Persian date string, and
         * records this field's calendar as Persian for subsequent
         * date rules ({@link #past()}, {@link #future()},
         * {@link #before(String)}, {@link #after(String)},
         * {@link #between(String, String)}, {@link #minimumAge(int)}).
         *
         * @return this field, for chaining
         */
        public Field<T, R> persianDate() {
            if (skipIfInactive()) return this;
            boolean ok = value != null && DateUtil.isValidPersian(asString());
            if (ok) {
                dateKind = DateUtil.Kind.PERSIAN;
                dateContextFailed = false;
            } else {
                dateKind = null;
                dateContextFailed = true;
            }
            return check(ok, "PERSIAN_DATE", fieldName + " must be a valid Persian date (yyyy/MM/dd)");
        }

        /**
         * Verifies that a prior {@link #date()} or {@link #persianDate()}
         * call succeeded on this field (i.e. {@link #dateKind} is set)
         * before letting a comparison-based date rule proceed. If
         * {@code date()}/{@code persianDate()} was called but already
         * failed on this field, that failure was already reported and no
         * second, redundant error is recorded here; a
         * {@code DATE_CONTEXT_MISSING} error is only recorded when no such
         * call was made at all.
         *
         * @return {@code true} if a calendar has been established for this field
         */
        private boolean hasDateContext() {
            if (dateKind == null) {
                if (!dateContextFailed) {
                    check(false, "DATE_CONTEXT_MISSING",
                            fieldName + ": date()/persianDate() must produce a valid date before this rule applies");
                }
                return false;
            }
            return true;
        }

        /**
         * Validates that a date string supplied for comparison ({@code otherDate}/{@code start}/{@code end})
         * is itself a valid date in this field's established calendar
         * ({@link #dateKind}); records an {@code INVALID_COMPARISON_DATE}
         * error otherwise.
         *
         * @param otherDate the comparison date string to validate
         * @param label     a short label identifying the parameter, used in the error message
         * @return {@code true} if {@code otherDate} is valid in this field's calendar
         */
        private boolean requireValidComparisonDate(String otherDate, String label) {
            if (otherDate != null && DateUtil.isValidForKind(dateKind, otherDate)) {
                return true;
            }
            check(false, "INVALID_COMPARISON_DATE",
                    fieldName + ": " + label + " ('" + otherDate + "') is not a valid "
                            + (dateKind == DateUtil.Kind.GREGORIAN ? "Gregorian" : "Persian")
                            + " date (yyyy/MM/dd)");
            return false;
        }

        /**
         * The date must be in the past (strictly before today, in this
         * field's established calendar). Requires a prior successful call
         * to {@link #date()} or {@link #persianDate()}.
         *
         * @return this field, for chaining
         */
        public Field<T, R> past() {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            boolean ok = value != null && DateUtil.toJdn(dateKind, asString())
                    < DateUtil.toJdn(dateKind, DateUtil.formatYmd(DateUtil.today(dateKind)));
            return check(ok, "PAST", fieldName + " must be in the past");
        }

        /**
         * The date must be in the future (strictly after today, in this
         * field's established calendar). Requires a prior successful call
         * to {@link #date()} or {@link #persianDate()}.
         *
         * @return this field, for chaining
         */
        public Field<T, R> future() {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            boolean ok = value != null && DateUtil.toJdn(dateKind, asString())
                    > DateUtil.toJdn(dateKind, DateUtil.formatYmd(DateUtil.today(dateKind)));
            return check(ok, "FUTURE", fieldName + " must be in the future");
        }

        /**
         * The date must be strictly before {@code otherDate} (same format,
         * same calendar as this field). Requires a prior successful call
         * to {@link #date()} or {@link #persianDate()}.
         *
         * @param otherDate the date to compare against
         * @return this field, for chaining
         */
        public Field<T, R> before(String otherDate) {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            if (!requireValidComparisonDate(otherDate, "otherDate")) return this;
            boolean ok = value != null && DateUtil.toJdn(dateKind, asString()) < DateUtil.toJdn(dateKind, otherDate);
            return check(ok, "BEFORE", fieldName + " must be before " + otherDate);
        }

        /**
         * The date must be strictly after {@code otherDate} (same format,
         * same calendar as this field). Requires a prior successful call
         * to {@link #date()} or {@link #persianDate()}.
         *
         * @param otherDate the date to compare against
         * @return this field, for chaining
         */
        public Field<T, R> after(String otherDate) {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            if (!requireValidComparisonDate(otherDate, "otherDate")) return this;
            boolean ok = value != null && DateUtil.toJdn(dateKind, asString()) > DateUtil.toJdn(dateKind, otherDate);
            return check(ok, "AFTER", fieldName + " must be after " + otherDate);
        }

        /**
         * The date must fall within {@code [start, end]} (same format, same
         * calendar as this field). Requires a prior successful call to
         * {@link #date()} or {@link #persianDate()}.
         *
         * @param start the start of the allowed range, inclusive
         * @param end   the end of the allowed range, inclusive
         * @return this field, for chaining
         */
        public Field<T, R> between(String start, String end) {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            if (!requireValidComparisonDate(start, "start")) return this;
            if (!requireValidComparisonDate(end, "end")) return this;
            boolean ok = false;
            if (value != null) {
                long v = DateUtil.toJdn(dateKind, asString());
                ok = v >= DateUtil.toJdn(dateKind, start) && v <= DateUtil.toJdn(dateKind, end);
            }
            return check(ok, "DATE_BETWEEN", fieldName + " must be between " + start + " and " + end);
        }

        /**
         * The age implied by this field, treated as a birth date, must be
         * at least {@code years} complete years (as of today, in this
         * field's established calendar). Requires a prior successful call
         * to {@link #date()} or {@link #persianDate()}.
         * <p>
         * A birth date in the future (yielding a negative age) always fails
         * this rule regardless of {@code years} and is reported as a
         * distinct {@code FUTURE_BIRTH_DATE} error rather than the generic
         * {@code MINIMUM_AGE} message, since "must represent an age of at
         * least N" would otherwise be a confusing thing to say about a date
         * that hasn't happened yet.
         *
         * @param years the minimum required age, in complete years
         * @return this field, for chaining
         */
        public Field<T, R> minimumAge(int years) {
            if (skipIfInactive()) return this;
            if (!hasDateContext()) return this;
            if (value == null) {
                return check(false, "MINIMUM_AGE", fieldName + " must represent an age of at least " + years);
            }
            int age = DateUtil.ageInYears(dateKind, asString());
            if (age < 0) {
                return check(false, "FUTURE_BIRTH_DATE", fieldName + " must not be a date in the future");
            }
            return check(age >= years, "MINIMUM_AGE", fieldName + " must represent an age of at least " + years);
        }

        // ───────────────────────── Collection ─────────────────────────

        /**
         * The collection (or map, or array) must not be empty. If this
         * field's value is not a {@link Collection}, {@link Map}, or array
         * (and is not {@code null}), the rule simply fails and records a
         * {@code NOT_EMPTY} error, the same as any other unsatisfied rule.
         *
         * @return this field, for chaining
         */
        public Field<T, R> notEmpty() {
            if (skipIfInactive()) return this;
            Collection<Object> collection = asCollection();
            return check(collection != null && !collection.isEmpty(), "NOT_EMPTY",
                    fieldName + " must not be empty");
        }

        /**
         * The collection (or map, or array) must have exactly the given
         * size. If this field's value is not collection-like, the rule
         * simply fails and records a {@code SIZE} error.
         *
         * @param size the required exact size
         * @return this field, for chaining
         */
        public Field<T, R> size(int size) {
            if (skipIfInactive()) return this;
            Collection<Object> collection = asCollection();
            return check(collection != null && collection.size() == size, "SIZE",
                    fieldName + " must have size " + size);
        }

        /**
         * The collection (or map, or array) must have at least the given
         * size. If this field's value is not collection-like, the rule
         * simply fails and records a {@code MIN_SIZE} error.
         *
         * @param min the minimum allowed size
         * @return this field, for chaining
         */
        public Field<T, R> minSize(int min) {
            if (skipIfInactive()) return this;
            Collection<Object> collection = asCollection();
            return check(collection != null && collection.size() >= min, "MIN_SIZE",
                    fieldName + " must have at least " + min + " elements");
        }

        /**
         * The collection (or map, or array) must have at most the given
         * size. If this field's value is not collection-like, the rule
         * simply fails and records a {@code MAX_SIZE} error.
         *
         * @param max the maximum allowed size
         * @return this field, for chaining
         */
        public Field<T, R> maxSize(int max) {
            if (skipIfInactive()) return this;
            Collection<Object> collection = asCollection();
            return check(collection != null && collection.size() <= max, "MAX_SIZE",
                    fieldName + " must have at most " + max + " elements");
        }

        /**
         * Every element of the collection (or map's entries, or array) must
         * be unique (no duplicates, as determined by {@code equals()}). If
         * this field's value is not collection-like, the rule simply fails
         * and records a {@code UNIQUE} error.
         * <p>
         * Being {@code equals()}-based can surprise for types where
         * {@code equals()} is stricter than what "the same value" might
         * suggest - e.g. {@code new BigDecimal("1.0")} and {@code new
         * BigDecimal("1.00")} are <em>not</em> {@code equals()} (they differ
         * in scale) even though {@code compareTo()} treats them as equal,
         * so both would be considered distinct, "unique" elements here.
         *
         * @return this field, for chaining
         */
        public Field<T, R> unique() {
            if (skipIfInactive()) return this;
            Collection<Object> collection = asCollection();
            boolean ok = collection != null && collection.size() == new HashSet<>(collection).size();
            return check(ok, "UNIQUE", fieldName + " must contain unique elements");
        }

        // ───────────────────────── Cross field ─────────────────────────

        /**
         * This field's value must equal the value returned by another
         * getter on the same target object (e.g. a "confirm password"
         * check).
         *
         * If {@code otherFieldGetter} itself throws, that exception is
         * caught and recorded as a normal {@code EQUAL_TO} validation error
         * rather than propagating out of {@link #validate()} - consistent
         * with this framework's "never throw, record an error" contract.
         *
         * @param otherFieldGetter getter for the field to compare against
         * @return this field, for chaining
         */
        public Field<T, R> equalTo(Function<T, R> otherFieldGetter) {
            if (skipIfInactive()) return this;
            try {
                R other = otherFieldGetter.apply(target);
                boolean ok = Objects.equals(value, other);
                return check(ok, "EQUAL_TO", fieldName + " must be equal to the compared field");
            } catch (RuntimeException e) {
                return check(false, "EQUAL_TO",
                        fieldName + ": failed to read compared field (" + e.getClass().getSimpleName()
                                + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
            }
        }
    }
}