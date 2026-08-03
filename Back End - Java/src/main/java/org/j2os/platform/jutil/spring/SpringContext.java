package org.j2os.platform.jutil.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Spring-managed helper that exposes static access to the application's
 * {@link ApplicationContext} and to the current HTTP request/response,
 * for use in places that are not themselves Spring beans (utility
 * classes, static factories, and the like) and therefore cannot simply
 * {@code @Autowired} what they need.
 * <p>
 * This class is registered as a Spring {@link Component}, which causes
 * Spring to call {@link #setApplicationContext(ApplicationContext)} once
 * during startup and populate the shared, static {@link #context} field.
 * Until that happens, {@link #getBean(Class)} and {@link #getBean(String)}
 * will throw {@link IllegalStateException}.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Getting a bean
 * MyService service = SpringContext.getBean(MyService.class);
 *
 * // Getting the current request
 * HttpServletRequest request = SpringContext.getHttpServletRequest();
 * }</pre>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@Component
public class SpringContext implements ApplicationContextAware {

    /**
     * The application's Spring context, populated once by Spring via
     * {@link #setApplicationContext(ApplicationContext)} during startup.
     * {@code null} until that callback has run.
     */
    private static ApplicationContext context;

    // ================== گرفتن Bean ==================

    /**
     * Looks up a Spring-managed bean by type.
     *
     * @param clazz the type of the bean to retrieve
     * @param <T>   the bean type
     * @return the bean instance managed by the application context
     * @throws IllegalStateException if the {@link ApplicationContext} has
     *                                not been initialized yet (i.e. Spring
     *                                has not yet called
     *                                {@link #setApplicationContext(ApplicationContext)})
     * @throws org.springframework.beans.BeansException if no matching
     *                                bean exists, or more than one match is
     *                                found and cannot be resolved
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext has not been initialized yet!");
        }
        return context.getBean(clazz);
    }

    /**
     * Looks up a Spring-managed bean by name.
     *
     * @param beanName the name of the bean to retrieve
     * @return the bean instance managed by the application context
     * @throws IllegalStateException if the {@link ApplicationContext} has
     *                                not been initialized yet (i.e. Spring
     *                                has not yet called
     *                                {@link #setApplicationContext(ApplicationContext)})
     * @throws org.springframework.beans.BeansException if no bean is
     *                                registered under {@code beanName}
     */
    public static Object getBean(String beanName) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext has not been initialized yet!");
        }
        return context.getBean(beanName);
    }

    // ================== گرفتن HttpServletRequest ==================

    /**
     * Returns the {@link HttpServletRequest} bound to the current thread
     * by Spring's {@link RequestContextHolder}, for use outside of a
     * controller method (e.g. in a service or utility class) while still
     * inside an active HTTP request.
     *
     * @return the current thread's {@link HttpServletRequest}
     * @throws IllegalStateException if there is no current HTTP request
     *                                bound to this thread (i.e. called
     *                                outside of request scope)
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException("There is no current HTTP request (outside of request scope)");
        }

        return attributes.getRequest();
    }

    // ================== گرفتن HttpServletResponse ==================

    /**
     * Returns the {@link HttpServletResponse} bound to the current thread
     * by Spring's {@link RequestContextHolder}, for use outside of a
     * controller method (e.g. in a service or utility class) while still
     * inside an active HTTP request.
     *
     * @return the current thread's {@link HttpServletResponse}
     * @throws IllegalStateException if there is no current HTTP request
     *                                bound to this thread (i.e. called
     *                                outside of request scope)
     */
    public static HttpServletResponse getHttpServletResponse() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException("There is no current HTTP request (outside of request scope)");
        }

        return attributes.getResponse();
    }

    /**
     * Spring callback invoked once during application startup with the
     * fully initialized {@link ApplicationContext}. Stores it in the
     * shared static {@link #context} field so it can be accessed from
     * {@link #getBean(Class)} and {@link #getBean(String)}.
     *
     * @param applicationContext the application context to store
     * @throws BeansException never thrown by this implementation; declared
     *                          because it is part of the
     *                          {@link ApplicationContextAware} contract
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
}