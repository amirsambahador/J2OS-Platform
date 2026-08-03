# J2OS Platform

**J2OS Platform is a modular Java toolkit:** A collection of independent Java libraries, all under the `org.j2os.platform.*` package, each with a single, self-contained responsibility (sharding, resilience, validation, a reflective RMI object cache, BPMS, report generation, security, general-purpose utilities). None of the modules depend on one another, except where explicitly noted (for example, `jvalidation` depends on `JDate` in `jutil.date`).

This document is written so that it can be read on its own, without reading the source, to understand what each module does, how it is used in a minimal standalone program, and how it is wired into a Spring Boot service.

**None of these modules require Spring, or any framework at all.** Every module is plain Java: the "Minimal example" under each one runs in a bare `main()` method with no framework in the classpath. The "Inside Spring Boot" examples exist only because Spring Boot is the most common framework this platform is deployed with — the same classes wire into Jakarta EE (CDI beans, `@ApplicationScoped`), Micronaut (`@Singleton`, `@Factory`), Quarkus, or a plain `main()`-based application exactly the same way: construct/configure the object once, keep the reference (as a CDI bean, a Micronaut singleton, a static field, however the host framework manages lifecycles), and call its methods. Nothing in `org.j2os.platform.*` imports a Spring class or requires a Spring annotation to function.

### Table of Contents

- [Module Index](#module-index)
- [Requirements & Dependencies](#requirements--dependencies)
- Backend modules: [JBalancer](#jbalancer) · [JCrux](#jcrux) · [JFlow](#jflow) · [JReport](#jreport) · [JSecurity - Access Control](#jsecurity---access-control) · [JSecurity - Cryptography](#jsecurity---cryptography) · [JSecurity - XSS Protection](#jsecurity---xss-protection) · [JShard](#jshard) · [JUtil - Date](#jutil---date-jdate) · [JUtil - JSON](#jutil---json-json) · [JUtil - Number](#jutil---number-jnumber) · [JValidation](#jvalidation) · [Page2](#page2) · [ResiCord](#resicord)
- [Combining Multiple Modules](#combining-multiple-modules)
- [Frontend (Vue / Nuxt)](#frontend-vue--nuxt)
- [Testing Conventions](#testing-conventions)
- [Full API Reference](#full-api-reference)
- [Design Notes](#design-notes)

---

## Requirements & Dependencies

Every module targets **Java 25** and is built as a Maven module under a Spring Boot 4.1.0 parent POM. None of the modules require Spring at all by themselves (they are plain Java libraries); Spring is only needed for the "Inside Spring Boot" examples in this document. The external libraries actually pulled in, module by module:

| Module | Key dependencies |
|---|---|
| JBalancer | none (pure `java.util.concurrent`) |
| JCrux | none beyond the JDK (`java.rmi`, `java.io`, reflection) |
| JFlow | `org.flowable:flowable-engine:8.0.0` |
| JReport | `net.sf.jasperreports:jasperreports`, `org.dynamicreports:dynamicreports-core:6.20.1` |
| JSecurity - Access Control | `org.apache.commons:commons-lang3` (reflection helpers), `org.slf4j:slf4j-api` |
| JSecurity - Cryptography | `org.bouncycastle:bcprov-jdk18on:1.84` |
| JSecurity - XSS Protection | `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260313.1`, Lombok (`@UtilityClass`) |
| JShard | `org.apache.shardingsphere:shardingsphere-jdbc:5.5.2`, `com.zaxxer:HikariCP`, `com.google.guava:guava:33.6.0-jre` |
| JUtil - Date | none beyond the JDK |
| JUtil - JSON | Jackson 3 (`tools.jackson.core` / `tools.jackson.databind` — note the `tools.jackson` package namespace, not the older `com.fasterxml.jackson`) |
| JUtil - Number | none beyond the JDK |
| JValidation | depends on [JUtil - Date](#jutil---date-jdate) for Persian-calendar rule support |
| Page2 | JPA (`jakarta.persistence`) for `PageDataEntity`; JDBC (`java.sql.Connection`) for `PageDataSQL`; Jackson 3 for `PageDataResultFilter` |
| ResiCord | none beyond the JDK (`java.util.concurrent`) |
| Frontend | Vue 3, Nuxt, Nuxt UI (`UTable`/`UPagination`), DOMPurify, IMask + `jalaali-js` (for `useDate`), Ace and SunEditor (loaded dynamically via `useFileLoader`, not bundled) |

Typical `pom.xml` starting point for a consumer application:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
</parent>

<properties>
    <java.version>25</java.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <!-- Add only the org.j2os.platform.* modules your application actually needs -->
</dependencies>
```

---

## Module Index

| Module | Package | Responsibility |
|---|---|---|
| [JBalancer](#jbalancer) | `org.j2os.platform.jbalancer` | Round-robin rotation across multiple URLs for a single logical resource |
| [JCrux](#jcrux) | `org.j2os.platform.jcrux` | A live Java object container, reachable over the network via RMI |
| [JFlow](#jflow) | `org.j2os.platform.jflow` | A BPMS engine (built on Flowable), reachable over the network via RMI |
| [JReport](#jreport) | `org.j2os.platform.jreport` | PDF/DOCX/XLSX report generation (Jasper/DynamicReports) |
| [JSecurity - Access](#jsecurity---access-control) | `org.j2os.platform.jsecurity.access` | Hiding or denying a field or an operation based on the caller's role |
| [JSecurity - Cryptography](#jsecurity---cryptography) | `org.j2os.platform.jsecurity.cryptography` | Hashing, encryption, key/token generation |
| [JSecurity - Protection](#jsecurity---xss-protection) | `org.j2os.platform.jsecurity.protection` | HTML sanitization against XSS |
| [JShard](#jshard) | `org.j2os.platform.jshard` | Database sharding built on ShardingSphere |
| [JUtil - Date](#jutil---date-jdate) | `org.j2os.platform.jutil.date` | Persian/Gregorian calendar conversion |
| [JUtil - JSON](#jutil---json-json) | `org.j2os.platform.jutil.json` | A thin JSON helper on top of Jackson |
| [JUtil - Number](#jutil---number-jnumber) | `org.j2os.platform.jutil.number` | Number-to-words conversion, Persian/Latin number formatting |
| [JValidation](#jvalidation) | `org.j2os.platform.jvalidation` | Fluent validation for any DTO/entity |
| [Page2](#page2) | `org.j2os.platform.page2` | Datagrid filtering/search/pagination (JPA, raw JPQL, raw SQL, in-memory lists) |
| [ResiCord](#resicord) | `org.j2os.platform.resicord` | Resilience: retry, timeout, bulkhead |

---

## JBalancer

A simple round-robin registry: for a logical identifier (e.g. a service name), you register several URLs, and each time you ask for the address you get the next one in rotation. It is a singleton.

### Minimal example

```java
import org.j2os.platform.jbalancer.JRoundRobinBalancer;

JRoundRobinBalancer.getInstance()
        .configurationResource("payment-service", List.of(
                "http://10.0.0.1:8080",
                "http://10.0.0.2:8080",
                "http://10.0.0.3:8080"));

String url = JRoundRobinBalancer.getInstance().getResourceUrl("payment-service");
// First call: 10.0.0.1, second: 10.0.0.2, third: 10.0.0.3, fourth wraps back to 10.0.0.1 ...
```

### Inside Spring Boot

```java
@Configuration
public class PaymentServiceConfig {

    @PostConstruct
    public void registerPaymentServiceUrls() {
        JRoundRobinBalancer.getInstance()
                .configurationResource("payment-service", List.of(
                        "http://10.0.0.1:8080",
                        "http://10.0.0.2:8080"));
    }
}

@Service
public class PaymentClient {

    private final RestClient restClient = RestClient.create();

    public PaymentResponse charge(PaymentRequest request) throws ResourceNotFoundException {
        String baseUrl = JRoundRobinBalancer.getInstance().getResourceUrl("payment-service");
        return restClient.post()
                .uri(baseUrl + "/charge")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }
}
```

---

## JCrux

**Design goal: a lightweight object container for a trusted internal network, not a public-facing service.** JCrux is built for a specific situation — a small, bounded set of Java objects (a handful of singleton services, shared configuration, a small registry) that a few JVMs on the same internal network need live, shared access to. `JCruxServer` communicates over RMI, authenticated by a shared token; it deliberately has no HTTP layer, no CORS handling, and no rate limiting of its own, because those concerns belong to whichever layer sits in front of it. Where an application needs HTTP/REST access to a container, the natural pattern is a thin `@RestController` (or the equivalent in another framework) built on top of `JCruxClient`, adding whatever authentication, rate-limiting, and CORS policy that endpoint needs — the same way any internal RMI/gRPC/message-queue service is normally fronted by an API gateway rather than exposed directly.

The same reasoning applies to scale: JCrux's role is holding a small number of controlled, "live" objects across a few JVMs — the kind of thing a Spring `@Bean` or a CDI singleton already is within one JVM, just made reachable from a few more. For a cache with many entries, eviction policies, or high throughput, Caffeine or Redis remain the right tool; JCrux and a general-purpose cache solve different problems.

`JCruxServer` is a general-purpose container: it stores any Java object, and lets other clients (in the same or a different JVM) construct it, invoke its methods, or read/write its fields — all gated behind a single token.

### Minimal example (server + client)

```java
// --- Server side ---
import org.j2os.platform.jcrux.server.JCruxServer;
import java.rmi.registry.LocateRegistry;
import java.rmi.Naming;

LocateRegistry.createRegistry(1099);
JCruxServer server = new JCruxServer("my-secret-token", "container.dat", true);
Naming.rebind("//localhost/config-container", server);

// --- Client side (same JVM, or a different one) ---
import org.j2os.platform.jcrux.client.JCruxClient;

JCruxClient client = new JCruxClient();
client.connect("localhost", "config-container", "my-secret-token");

String id = client.create("org.j2os.examples.AppConfig", "production", 30);
Object configValue = client.invoke(id, "getTimeoutSeconds");
client.setField(id, "timeoutSeconds", 60);
```

### Inside Spring Boot (with a thin REST layer on top)

```java
@Configuration
public class JCruxConfig {

    @Bean
    public JCruxClient jCruxClient() throws Exception {
        JCruxClient client = new JCruxClient();
        client.connect("localhost", "config-container", System.getenv("JCRUX_TOKEN"));
        return client;
    }
}

@RestController
@RequestMapping("/internal/config")
public class ConfigController {

    private final JCruxClient jCruxClient;

    public ConfigController(JCruxClient jCruxClient) {
        this.jCruxClient = jCruxClient;
    }

    // Endpoint-level authentication (e.g. @PreAuthorize, or a filter/interceptor) belongs
    // here, at the HTTP layer this controller adds on top of JCrux.
    @GetMapping("/{id}/{field}")
    public Object getField(@PathVariable String id, @PathVariable String field) throws Exception {
        return jCruxClient.getField(id, field);
    }
}
```

---

## JFlow

**Same design goal as [JCrux](#jcrux): built for use within a trusted internal network, fronted by an application-provided HTTP layer where needed** — `JFlowServer` exports a Flowable engine behind RMI the same way `JCruxServer` exports its object container.

`JFlowServer` is a complete BPMS engine (built on Flowable): starting/managing process instances, completing tasks, rendering diagrams, deploying BPMN definitions — all gated behind a single token.

### Minimal example

```java
// --- Server side ---
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.j2os.platform.jflow.server.JFlowServer;

ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
        .setJdbcUrl("jdbc:postgresql://localhost:5432/flowable")
        .setJdbcUsername("flowable")
        .setJdbcPassword("secret")
        .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

JFlowServer server = new JFlowServer("flow-token", cfg);
LocateRegistry.createRegistry(1100);
Naming.rebind("//localhost:1100/flow-engine", server);

// When the server is no longer needed (application shutdown, end of a test):
server.close();

// --- Client side ---
import org.j2os.platform.jflow.client.JFlowClient;

JFlowClient client = new JFlowClient("localhost:1100", "flow-engine", "flow-token");
client.deployFromFileAndGetProcessDefinitionKey("leave-request.bpmn20.xml");
String processInstanceId = client.startProcessAndGetProcessInstanceId(
        "leave-request", Map.of("employeeId", 42, "days", 3));

List<JFlowTask> openTasks = client.getOpenTasksByAssignee("manager-1");
client.signalByTaskId(openTasks.get(0).getTaskId(), Map.of("approved", true));
```

### Inside Spring Boot

```java
@Configuration
public class JFlowConfig {

    @Bean(destroyMethod = "close")
    public JFlowServer jFlowServer(@Value("${jflow.token}") String token) throws Exception {
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:postgresql://localhost:5432/flowable")
                .setJdbcUsername("flowable")
                .setJdbcPassword(System.getenv("FLOWABLE_DB_PASSWORD"))
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        return new JFlowServer(token, cfg);
        // Spring itself invokes destroyMethod="close" on shutdown.
    }
}

@RestController
@RequestMapping("/internal/leave-requests")
public class LeaveRequestController {

    private final JFlowServer jFlowServer; // Called directly, since this is the same JVM

    public LeaveRequestController(JFlowServer jFlowServer) {
        this.jFlowServer = jFlowServer;
    }

    @PostMapping
    public String start(@RequestBody LeaveRequest request, @Value("${jflow.token}") String token) throws Exception {
        return jFlowServer.startProcessAndGetProcessInstanceId(
                token, "leave-request", Map.of("employeeId", request.employeeId(), "days", request.days()));
    }
}
```

---

## JReport

JReport can produce a report in three distinct ways, depending on where the data comes from. All three write to either an `HttpServletResponse` (`generateToResponse`, for a download endpoint) or a file on disk (`generateToFile`), and all three support the same three export formats (`ReportType.PDF`, `.DOCX`, `.XLSX`).

1. **`DynamicReport` — fully dynamic, connection-based, no design file.** You give it a live JDBC `Connection` and a raw SQL query directly in code, along with the column names to show and their header labels. There is no separate template to design — the whole report layout is generated automatically from the column list. This is the fastest way to get a report out, at the cost of layout control.
2. **`TemplateReport` — connection-based, with a designed template.** You give it a live JDBC `Connection` and the classpath location of a `.jrxml` file — a report layout designed visually beforehand (fonts, positioning, branding, page structure). Unlike `DynamicReport`, the SQL query itself is **not** passed in code; it is embedded inside the `.jrxml` template and is executed against the `Connection` you supply.
3. **`EntityTemplateReport` — entity-based, with a designed template, no connection at all.** Same kind of `.jrxml` template as `TemplateReport`, but instead of running a query against a live connection, you hand it data you have already fetched yourself — a `List<Map<String, Object>>` of rows, typically built from JPA entities, a REST call, or any other source. This is the right choice when the data doesn't come from a single simple SQL query, or when the report needs to combine data from more than one place.

In short: reach for `DynamicReport` when there's no design file and none is needed; reach for `TemplateReport` when there is a design file and the data is a straightforward query against a live connection; reach for `EntityTemplateReport` when there is a design file but the data is assembled in Java first.

### Minimal example

```java
import org.j2os.platform.jreport.dynamic.DynamicReport;
import org.j2os.platform.jreport.report.ReportType;

// DynamicReport - the fully dynamic, connection-based mode: no template file at all.
DynamicReport.generateToFile(
        "sales-report.pdf",
        connection,
        "sales-report",
        ReportType.PDF,
        "Monthly Sales Report",
        "SELECT product_name, quantity, total_price FROM sales WHERE month = ?",
        List.of("product_name", "quantity", "total_price"),
        List.of("Product", "Quantity", "Total"));
```

### Inside Spring Boot (all three modes)

```java
@RestController
@RequestMapping("/reports")
public class SalesReportController {

    private final DataSource dataSource;

    public SalesReportController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Mode 1: DynamicReport - connection-based, fully dynamic, no template file.
    @GetMapping("/sales")
    public void downloadSalesReport(HttpServletResponse response) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DynamicReport.generateToResponse(
                    response, connection, "sales-report", ReportType.PDF, "Sales Report",
                    "SELECT product_name, quantity, total_price FROM sales",
                    List.of("product_name", "quantity", "total_price"),
                    List.of("Product", "Quantity", "Total"));
        }
    }

    // Mode 2: TemplateReport - connection-based, with a designed template. The SQL query
    // lives inside invoice-template.jrxml itself, not here in the Java code; it is executed
    // against the Connection this method supplies.
    @GetMapping("/invoice-from-query/{orderId}")
    public void downloadInvoiceFromEmbeddedQuery(@PathVariable long orderId, HttpServletResponse response) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            TemplateReport.generateToResponse(
                    response, connection, "/reports/invoice-template.jrxml", ReportType.PDF,
                    Map.of("orderId", orderId));
        }
    }

    // Mode 3: EntityTemplateReport - entity-based, with the same kind of designed template,
    // but no Connection at all. The rows are fetched however the application likes (here,
    // via a JPA repository) and handed in already assembled.
    @GetMapping("/invoice-from-entities/{orderId}")
    public void downloadInvoiceFromEntities(@PathVariable long orderId, HttpServletResponse response,
                                             OrderRepository orderRepository) throws Exception {
        List<Map<String, Object>> lineItems = orderRepository.findLineItemsAsMaps(orderId);
        EntityTemplateReport.generateToResponse(
                response, "/reports/invoice-template.jrxml", ReportType.PDF,
                Map.of("orderId", orderId), lineItems);
    }
}
```

---

## JSecurity - Access Control

Two classes cover the two ends of a request. `RequestAccessControl` is meant to run **at the start** of handling a request — right after the incoming payload is received and before it's processed further — restricting which fields a given role/action combination may set, view, or touch on an object, or denying the whole action outright before any work begins. `ResponseAccessControl` is meant to run **at the end** — right before an already-built response (typically a `page2` grid result) leaves the application — reshaping the outgoing data by removing, blanking, or masking fields, independent of whatever restrictions were applied on the way in.

### Minimal example

```java
import org.j2os.platform.jsecurity.access.RequestAccessControl;

// Registered once, at application startup
RequestAccessControl.registerFieldLimitation("EMPLOYEE", User.class, "salary", "VIEW");
RequestAccessControl.registerActionDenial("EMPLOYEE", User.class, "DELETE");

// Called at the start of handling a request - here, restricting a User object for the
// current role before any further processing happens.
User restrictedUser = RequestAccessControl.apply(currentUserRole, user, "VIEW");
// restrictedUser.getSalary() == null if the current role is EMPLOYEE
```

### Inside Spring Boot

```java
@Configuration
public class AccessControlConfig {

    @PostConstruct
    public void registerRestrictions() {
        RequestAccessControl.registerFieldLimitation("EMPLOYEE", User.class, "salary", "VIEW");
        RequestAccessControl.registerFieldLimitation("EMPLOYEE", User.class, "nationalCode", "VIEW");
        RequestAccessControl.registerActionDenial("EMPLOYEE", User.class, "DELETE");
    }
}

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        User user = userRepository.findById(id).orElseThrow();
        // RequestAccessControl at the start of handling this request, restricting the object
        // for the current role before it's returned - salary/nationalCode come back null for EMPLOYEE.
        return RequestAccessControl.apply(currentUser.getRole(), user, "VIEW");
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        // Checked at the very start, before any deletion work happens: if the current role
        // is EMPLOYEE, this throws DeniedException right here and userRepository.deleteById
        // below is never reached.
        RequestAccessControl.apply(currentUser.getRole(), userRepository.findById(id).orElseThrow(), "DELETE");
        userRepository.deleteById(id);
    }

    @GetMapping
    public Map<String, Object> listUsers(@RequestParam Map<String, Object> gridParams,
                                          EntityManager entityManager) {
        // ResponseAccessControl at the end: page2 has already built the full grid response;
        // this reshapes it right before it goes out, independent of anything RequestAccessControl did.
        Map<String, Object> page2Result = new PageDataEntity(entityManager)
                .searchAndSortOn("name", "email")
                .getResult(User.class, gridParams);

        return new ResponseAccessControl()
                .apply(page2Result, List.of("salary", "nationalCode"), ResponseAccessControl.EMPTY);
    }
}
```

---

## JSecurity - Cryptography

A static utility class for password hashing (BCrypt/Argon2/PBKDF2), file/string hashing (MD5/SHA), AES/RSA encryption, and generating secure random values (UUID, numbers, strings).

### Minimal example

```java
import org.j2os.platform.jsecurity.cryptography.Cryptography;

String hashed = Cryptography.hashByBCrypt("myPassword123");
boolean matches = Cryptography.checkByBCrypt("myPassword123", hashed); // true

SecretKey key = Cryptography.generateAESKey(256);
String encrypted = Cryptography.encryptStringByAES("confidential text", key);
String decrypted = Cryptography.decryptStringByAES(encrypted, key);

String sessionToken = Cryptography.uuid();
```

### Inside Spring Boot

```java
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void register(String username, String rawPassword) {
        User user = new User(username, Cryptography.hashByBCrypt(rawPassword));
        userRepository.save(user);
    }

    public boolean login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return Cryptography.checkByBCrypt(rawPassword, user.getPasswordHash());
    }
}
```

---

## JSecurity - XSS Protection

Three modes for sanitizing user-supplied HTML: `toRichText` (keeps a curated set of safe formatting tags), `toPlainText` (strips all markup entirely), and `toDisplayHtml` (escapes markup so it is shown literally, rather than removing it).

### Minimal example

```java
import org.j2os.platform.jsecurity.protection.XssProtector;

XssProtector protector = new XssProtector();
String safe = protector.toRichText("<b>Hello</b><script>alert(1)</script>");
// Result: <b>Hello</b>
```

### Inside Spring Boot

```java
@RestController
@RequestMapping("/comments")
public class CommentController {

    private final XssProtector xssProtector = new XssProtector();
    private final CommentRepository commentRepository;

    public CommentController(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @PostMapping
    public Comment addComment(@RequestBody CommentRequest request) {
        Comment comment = new Comment();
        comment.setBody(xssProtector.toRichText(request.body()));
        return commentRepository.save(comment);
    }
}
```

---

## JShard

Database sharding: you define several shards and several tables, and get back a plain `DataSource` that Hibernate/JPA treats exactly like an ordinary database. A shard can optionally have one or more replicas for read/write splitting — writes always go to the primary, and reads are load-balanced across the replicas automatically.

### Minimal example

```java
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.config.JShardTableConfig;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;

Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
shards.put("shard-a", List.of(JShardConnectionConfig.builder()
        .driverClassName("org.postgresql.Driver")
        .jdbcUrl("jdbc:postgresql://db1:5432/app")
        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build()));
shards.put("shard-b", List.of(JShardConnectionConfig.builder()
        .driverClassName("org.postgresql.Driver")
        .jdbcUrl("jdbc:postgresql://db2:5432/app")
        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build()));

JShardDataSourceProvider.assertAllReachable(shards); // checks every shard's health before build()

DataSource dataSource = JShardDataSourceProvider.builder()
        .shards(shards)
        .table("order_tbl", "order_id") // the column that decides which shard a record goes to
        .showSql(true)
        .build();
```

### Read/write splitting: giving a shard one or more replicas

Pass a primary connection plus any number of replica connections to `.shard(name, primary, replicas...)`. Every write goes to the primary; every read is load-balanced across the replicas. This applies per shard, and each shard can have a different number of replicas (or none at all):

```java
DataSource dataSource = JShardDataSourceProvider.builder()
        .shard("shard-a",
                JShardConnectionConfig.builder() // primary - receives all writes
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db1-primary:5432/app")
                        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build(),
                JShardConnectionConfig.builder() // replica #1 - receives reads
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db1-replica-1:5432/app")
                        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build(),
                JShardConnectionConfig.builder() // replica #2 - also receives reads
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db1-replica-2:5432/app")
                        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build())
        .shard("shard-b", // this shard has no replicas - all traffic goes to its single connection
                JShardConnectionConfig.builder()
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db2:5432/app")
                        .username("app").password(System.getenv("DB_PASSWORD")).poolSize(10).build())
        .table("order_tbl", "order_id")
        .build();
```

### Insert and select, once the DataSource is built

From here on you use the `DataSource` exactly like a normal one — plain JDBC, or JPA/Hibernate on top of it. Routing to the correct shard, and to a replica for reads, happens automatically based on the sharding column you configured:

```java
// Plain JDBC
try (Connection connection = dataSource.getConnection()) {
    try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO order_tbl (order_id, customer_id, total) VALUES (?, ?, ?)")) {
        insert.setLong(1, 1001L);
        insert.setLong(2, 42L);
        insert.setBigDecimal(3, new BigDecimal("129.90"));
        insert.executeUpdate();
    }

    try (PreparedStatement select = connection.prepareStatement(
            "SELECT * FROM order_tbl WHERE order_id = ?")) {
        select.setLong(1, 1001L);
        ResultSet rows = select.executeQuery();
    }
}
```

```java
// JPA / Hibernate, using the EntityManagerFactory built on top of the same DataSource
@Entity
@Table(name = "order_tbl")
public class Order {
    @Id
    private Long orderId;
    private Long customerId;
    private BigDecimal total;
    // getters/setters
}

Order order = new Order();
order.setOrderId(1001L);
order.setCustomerId(42L);
order.setTotal(new BigDecimal("129.90"));
entityManager.persist(order); // routed to the correct shard's primary automatically

Order found = entityManager.find(Order.class, 1001L); // routed to a replica if the shard has any
```

### Inside Spring Boot

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.shard.repository",
        entityManagerFactoryRef = "shardEntityManagerFactory",
        transactionManagerRef = "shardTransactionManager")
public class ShardConfig {

    @Bean
    public DataSource shardDataSource() throws Exception {
        Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
        shards.put("shard-a", List.of(JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl(System.getenv("SHARD_A_URL"))
                .username(System.getenv("SHARD_A_USER"))
                .password(System.getenv("SHARD_A_PASSWORD"))
                .poolSize(10).build()));
        shards.put("shard-b", List.of(JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl(System.getenv("SHARD_B_URL"))
                .username(System.getenv("SHARD_B_USER"))
                .password(System.getenv("SHARD_B_PASSWORD"))
                .poolSize(10).build()));

        JShardDataSourceProvider.assertAllReachable(shards);
        return JShardDataSourceProvider.builder()
                .shards(shards)
                .table("order_tbl", "order_id")
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean shardEntityManagerFactory(
            @Qualifier("shardDataSource") DataSource dataSource, EntityManagerFactoryBuilder builder) {
        return builder.dataSource(dataSource).packages("com.example.shard.entity")
                .persistenceUnit("shard").build();
    }

    @Bean
    public PlatformTransactionManager shardTransactionManager(
            @Qualifier("shardEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}

// From here on, OrderRepository is an ordinary JpaRepository; JShard decides, behind the
// scenes, which shard (and which replica, for reads) each order is routed to.
@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(Order order) {
        return orderRepository.save(order);   // write -> primary
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(); // read -> a replica, if the shard has any
    }
}
```

---

## JUtil - Date (JDate)

Persian/Gregorian calendar conversion, with no external library dependency.

### Minimal example

```java
import org.j2os.platform.jutil.date.JDate;

JDate today = new JDate();
System.out.println(today.getPersianDate()); // "1403/05/12"

JDate specific = new JDate();
specific.setPersianDate(1403, 1, 1);
System.out.println(specific.getGregorianDate()); // "2024/03/20"

specific.nextDay(10);
```

### Inside Spring Boot

```java
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    @GetMapping("/{id}/persian-date")
    public String getPersianIssueDate(@PathVariable Long id, InvoiceRepository repo) {
        Invoice invoice = repo.findById(id).orElseThrow();
        JDate date = new JDate(invoice.getIssueYear(), invoice.getIssueMonth(), invoice.getIssueDay());
        return date.getPersianDate();
    }
}
```

---

## JUtil - JSON (JSon)

A thin layer over Jackson for common tasks (parsing, and reading a single nested field without a full deserialization).

### Minimal example

```java
import org.j2os.platform.jutil.json.JSon;

User user = JSon.read(jsonString, User.class);
String json = JSon.write(user);

String city = JSon.readFieldAsText(jsonString, "address", "city");
```

### Inside Spring Boot

```java
@Service
public class WebhookService {

    public void handleIncomingWebhook(String rawJsonBody) {
        String eventType = JSon.readFieldAsText(rawJsonBody, "type");
        if ("payment.succeeded".equals(eventType)) {
            PaymentEvent event = JSon.read(rawJsonBody, PaymentEvent.class);
            // ...
        }
    }
}
```

---

## JUtil - Number (JNumber)

Number-to-words conversion (Persian/English) and number formatting with thousands separators, along with conversion between Persian and Latin digits.

### Minimal example

```java
import org.j2os.platform.jutil.number.JNumber;

JNumber.getPersianWords("125000");     // "one hundred twenty-five thousand" (spelled out in Persian)
JNumber.getPersianNumber("1234567");   // "۱,۲۳۴,۵۶۷"
JNumber.getEnglishNumber("۱۲۳۴۵۶۷");   // "1,234,567"
```

### Inside Spring Boot

```java
@RestController
@RequestMapping("/invoices")
public class InvoicePrintController {

    @GetMapping("/{id}/amount-in-words")
    public String amountInWords(@PathVariable Long id, InvoiceRepository repo) {
        Invoice invoice = repo.findById(id).orElseThrow();
        return JNumber.getPersianWords(invoice.getTotalAmount().toString()) + " Rials";
    }
}
```

---

## JValidation

Fluent validation using getter references (not string field names) — meaning a typo in a field reference is caught at compile time, not at runtime.

### Minimal example

```java
import org.j2os.platform.jvalidation.Validator;
import org.j2os.platform.jvalidation.Patterns;

ValidationResult result = Validator.of(user)
        .field(User::getMobile).required().regex(Patterns.MOBILE)
        .field(User::getEmail).required().regex(Patterns.EMAIL)
        .field(User::getAge).number().min(18)
        .validate();

if (!result.isValid()) {
    for (ValidationResult.Error error : result.errors()) {
        System.out.println(error.getField() + ": " + error.getMessage());
    }
}
```

### Inside Spring Boot

```java
@RestController
@RequestMapping("/users")
public class RegistrationController {

    @PostMapping
    public ResponseEntity<?> register(@RequestBody User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getMobile).required().regex(Patterns.MOBILE)
                .field(User::getNationalCode).required().regex(Patterns.NATIONAL_CODE)
                .field(User::getPassword).required().minLength(8)
                .validate();

        if (!result.isValid()) {
            return ResponseEntity.badRequest().body(result.errors());
        }
        // continue with registration ...
        return ResponseEntity.ok().build();
    }
}
```

---

## Page2

Page2 answers one recurring need: turn "give me page 2, sorted by name, filtered to my organization, searching for 'john'" into an actual result — filtered, sorted, paginated, and shaped as `{total, rows, page, size, search}` — without writing that logic by hand for every grid in the application. Every class shares the same request-parameter contract (`gridParams`: `page`, `rows`, `sort`, `order`, `q`) and the same output shape; what differs is where the underlying data comes from. Five classes make up the module — four alternative data sources that each produce a `gridParams`-shaped result, plus one class (`PageDataResultFilter`) that reshapes any of those four results afterward.

### `PageDataEntity` — the default choice for a JPA entity

Use this whenever the grid is backed by a single JPA entity and the filters map directly onto that entity's fields — no join, no computed column, nothing a plain JPQL `where` clause on one entity can't express. It builds the JPQL query internally from the conditions registered via `where`/`and`/`or`.

```java
import org.j2os.platform.page2.PageDataEntity;

Map<String, Object> gridParams = Map.of("page", "1", "rows", "20", "sort", "name", "order", "ASC", "q", "john");

Map<String, Object> result = new PageDataEntity(entityManager)
        .where("organizationId", "=", currentOrgId) // a fixed security filter the caller cannot override
        .and("active", "=", true)                    // combined with AND; use .or(...) to combine with OR instead
        .searchAndSortOn("name", "email")             // the ONLY fields "sort" and the free-text "q" may touch
        .getResult(User.class, gridParams);
// result: {total: 42, rows: [...20 User objects...], page: 1, size: 20, search: "john"}
```

### `PageDataJPQL` — for a query `PageDataEntity` can't express

Use this once the query needs something a single-entity filter can't do: a join across entities, a computed/aggregated column, or JPQL you'd rather write by hand. You supply the content query and a matching count query yourself; Page2 still handles paging, sorting, and search on top of them.

```java
import org.j2os.platform.page2.PageDataJPQL;

Map<String, Object> result = new PageDataJPQL(entityManager)
        .searchAndSortOn("o.customerName", "o.status") // fields must be prefixed with the alias used below
        .getResult(
                "select o from Order o join o.customer c where c.organizationId = :orgId",
                "select count(o) from Order o join o.customer c where c.organizationId = :orgId",
                "o",                       // the entity alias used in both queries above ("o" for Order)
                Order.class,               // the type each row of the content query resolves to
                Map.of("orgId", currentOrgId), // base parameters for the queries above, separate from gridParams
                gridParams);
```

### `PageDataSQL` — for raw SQL, no JPA entity involved at all

Use this when there's no JPA entity to query at all — a reporting view, a legacy table, a query that's simplest to write as plain SQL. It takes a JDBC `Connection` directly rather than an `EntityManager`, and each row of the result comes back as a `Map<String, Object>` rather than a typed entity.

```java
import org.j2os.platform.page2.PageDataSQL;

Map<String, Object> result = new PageDataSQL(connection)
        .searchAndSortOn("customer_name", "status")
        .getResult(
                "select * from order_summary_view where org_id = :orgId",
                Map.of("orgId", currentOrgId), // base SQL parameters, bound safely (not string-concatenated)
                gridParams);
// result: {total, rows: [...Map<String,Object> rows...], page, size, search}
```

### `PageDataList` — for a list you already have in memory

Use this when the data isn't in a database at all by the time it needs paging — the result of an external API call already fetched into a `List`, a small reference dataset cached at startup, or the output of some other computation. No constructor arguments are needed; the filtering, sorting, and paging all happen in memory via reflection over the objects in the list.

```java
import org.j2os.platform.page2.PageDataList;

List<User> allUsersAlreadyInMemory = externalUserService.fetchAllUsers(); // e.g. already fully loaded

Map<String, Object> result = new PageDataList()
        .where("active", "=", true)
        .searchAndSortOn("name", "email")
        .getResult(allUsersAlreadyInMemory, gridParams);
```

### `PageDataResultFilter<T>` — reshaping any of the four results above

`PageDataResultFilter` is a separate, optional last step: it wraps a `getResult()` map (from any of the four classes above) and lets you remove, blank, mask, or replace fields on every row before the result leaves the application — typically right before it's serialized into an HTTP response. It is single-use: `getResult()` may be called exactly once per instance, so a fresh `PageDataResultFilter` is created for each grid response.

| Method | What it does to each row |
|---|---|
| `.remove(field)` | Deletes the field entirely — the key is absent from the output row |
| `.empty(field)` | Replaces the field's value with an empty string, but keeps the key present |
| `.mask(field)` | Replaces the field's value with a fixed mask string (`"********"`) |
| `.put(field, Function<T, Object>)` | Sets/adds the field using a function of the row's original entity alone |
| `.put(field, Class<V>, BiFunction<T, V, V>)` | Sets/replaces the field using a function of the row's original entity **and** the field's current value (converted to the given type first) |
| `.getResult()` | Applies every registered rule to every row and returns the reshaped map — callable once |

```java
import org.j2os.platform.page2.PageDataResultFilter;

Map<String, Object> rawResult = new PageDataEntity(entityManager)
        .searchAndSortOn("name", "email")
        .getResult(User.class, gridParams);

Map<String, Object> shaped = new PageDataResultFilter<User>(rawResult)
        .remove("passwordHash")                 // never leaves the application at all
        .mask("nationalCode")                    // shown to the client as "********"
        .empty("internalNotes")                  // key stays present, but blank
        // put(field, Function<T,Object>) - compute a brand-new field from the row's entity
        .put("fullName", user -> user.getFirstName() + " " + user.getLastName())
        // put(field, Class<V>, BiFunction<T,V,V>) - transform a field's EXISTING value, using
        // both the entity and the field's current value (converted to Integer here first)
        .put("age", Integer.class, (user, currentAge) ->
                currentAge != null && currentAge >= 18 ? currentAge : null)
        .getResult();
// calling .getResult() again on this same instance throws IllegalStateException
```

### Inside Spring Boot

```java
@RestController
@RequestMapping("/users")
public class UserGridController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, Object> gridParams,
                                     @AuthenticationPrincipal AppUser currentUser) {
        Map<String, Object> result = new PageDataEntity(entityManager)
                .where("organizationId", "=", currentUser.getOrganizationId())
                .searchAndSortOn("name", "email", "mobile")
                .getResult(User.class, gridParams);

        // Strip sensitive fields before returning the response to the frontend
        return new PageDataResultFilter<User>(result)
                .remove("passwordHash")
                .mask("nationalCode")
                .getResult();
    }
}
```

---

## ResiCord

ResiCord wraps a unit of work — typically a call to something that can fail or run slow, like a network request or a database query — with up to three independent resilience behaviors, attached in a fluent chain and triggered by `.get()`:

- **Retry** — re-run the work automatically if it fails, up to a maximum number of attempts, with a fixed delay between attempts.
- **Time limit** — fail the work if it runs longer than a given budget, rather than letting a hung call block forever.
- **Bulkhead** — cap how many callers can run this particular kind of work at the same time, via a dedicated thread pool and semaphore *specific to that named resource* — so, for example, a slow payment gateway can't exhaust threads that other parts of the application need. A `BulkheadPolicy(maxConcurrentThreads, maxQueueSize, maxWaitMillis)` means: up to `maxConcurrentThreads` callers run at once; beyond that, up to `maxQueueSize` more callers wait in line; a caller that's still waiting after `maxWaitMillis` gives up and fails with a rejection, rather than waiting indefinitely.

Any of the three can be skipped, and the three that are used can be combined in any order in the chain. Each can be configured two ways:

- **Inline**, with raw numbers (`.retry(3, 500)`, `.timeLimit(2000)`) — fine for a one-off call or a quick prototype.
- **By name**, via a policy registered once in `RetryPolicies` / `TimeLimitPolicies` / `BulkheadPolicies` and referenced everywhere by that name (`.retry("payment-gateway")`) — the better choice once more than one call site needs the same resilience behavior, since the configuration then lives in exactly one place instead of being copy-pasted at every call site.

A capability worth calling out specifically: **named policies can be changed at runtime, without restarting the application.** `BulkheadPolicies.reconfigure(name, newPolicy)` resizes an already-running pool in place — every caller that's already using that name by reference (not by copying the old policy values) picks up the new limits on its very next call, with no redeploy needed. This is useful for reacting to a live incident (e.g. temporarily lowering a downstream service's allowed concurrency) or gradually raising a limit as confidence in a new integration grows. `RetryPolicies`/`TimeLimitPolicies` don't have a `reconfigure` of their own — call `.define(name, newPolicy)` again with the same name to replace their setting.

### Minimal example

```java
import org.j2os.platform.resicord.Try;
import org.j2os.platform.resicord.retry.RetryPolicies;
import org.j2os.platform.resicord.retry.RetryPolicy;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicies;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicy;

// Defined once, at application startup
RetryPolicies.define("payment-gateway", new RetryPolicy(3, 500));       // up to 3 attempts, 500ms apart
BulkheadPolicies.define("payment-gateway", new BulkheadPolicy(10, 20, 2000));
// -> at most 10 concurrent calls; up to 20 more callers queue; a queued caller waiting past
//    2000ms gives up and fails, rather than blocking indefinitely

// Used anywhere in the application, as needed
String result = new Try<>(() -> callPaymentGateway(request))
        .retry("payment-gateway")
        .bulkhead("payment-gateway")
        .timeLimit(3000)               // inline this time: fail if a single attempt exceeds 3s
        .onError(e -> {
            // onError supplies a fallback value instead of letting the failure propagate as
            // an exception - used here to log and degrade gracefully rather than fail the caller.
            log.error("Payment gateway call failed", e);
            return "FAILED";
        })
        .get();
```

### Dynamic reconfiguration at runtime

```java
// Somewhere in an admin endpoint, a config-reload listener, or a runbook script -
// no application restart, no redeploy, and every in-flight and future caller of
// "payment-gateway" picks this up on its next call.
BulkheadPolicy current = BulkheadPolicies.currentConfig("payment-gateway");
System.out.println("current limit: " + current.maxConcurrentThreads());

// Scale the pool up, e.g. because the downstream service confirmed it can take more load.
BulkheadPolicies.reconfigure("payment-gateway", new BulkheadPolicy(25, 50, 2000));

// Or scale it down quickly during an incident, without touching any calling code.
BulkheadPolicies.reconfigure("payment-gateway", new BulkheadPolicy(3, 5, 1000));

// listAll() / remove(name) work the same way across all three policy registries -
// useful for an admin dashboard listing every configured resilience policy in the app.
Map<String, BulkheadPolicy> everyBulkheadPolicy = BulkheadPolicies.listAll();
```

### Inside Spring Boot

```java
@Configuration
public class ResilienceConfig {

    @PostConstruct
    public void definePolicies() {
        RetryPolicies.define("payment-gateway", new RetryPolicy(3, 500));
        BulkheadPolicies.define("payment-gateway", new BulkheadPolicy(10, 20, 2000));
        TimeLimitPolicies.define("payment-gateway", new TimeLimitPolicy(3000));
    }
}

@Service
public class PaymentGatewayClient {

    private final RestClient restClient = RestClient.create();

    public PaymentResponse charge(PaymentRequest request) {
        return new Try<>(() -> restClient.post()
                .uri("https://gateway.example.com/charge")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class))
                .retry("payment-gateway")
                .bulkhead("payment-gateway")
                .timeLimit("payment-gateway")
                .onError(e -> { throw new PaymentGatewayException(e); }) // rethrow instead of a fallback value
                .get();
    }

    // execute(...) is a shortcut for when only bulkhead-bounding is needed, with no retry
    // or time limit - runs the task under the named pool directly, without building a Try at all.
    public PaymentResponse chargeWithBulkheadOnly(PaymentRequest request) throws Exception {
        return BulkheadPolicies.execute("payment-gateway", () -> restClient.post()
                .uri("https://gateway.example.com/charge")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class));
    }
}
```

---

## Combining Multiple Modules

This section shows how several modules are typically used together — something the individual examples above don't demonstrate on their own.

### JBalancer + ResiCord — calling a multi-node service with real retry and failover

Simply by registering several URLs in JBalancer, every retry attempt automatically moves on to the next node (because each attempt calls `getResourceUrl` again) — with no failover logic to write yourself:

```java
@Service
public class NotificationClient {

    private final RestClient restClient = RestClient.create();

    @PostConstruct
    public void registerNodes() {
        JRoundRobinBalancer.getInstance().configurationResource("notification-service", List.of(
                "http://notify-1:8080", "http://notify-2:8080", "http://notify-3:8080"));
        RetryPolicies.define("notification-service", new RetryPolicy(3, 300));
    }

    public void send(NotificationRequest request) {
        new Try<>(() -> {
            // Each attempt asks JBalancer for a URL again; if the first node is down,
            // the second attempt goes to the second node.
            String url = JRoundRobinBalancer.getInstance().getResourceUrl("notification-service");
            restClient.post().uri(url + "/send").body(request).retrieve().toBodilessEntity();
            return null;
        }).retry("notification-service").get();
    }
}
```

### ResiCord + JShard — protecting shard queries against one slow/unreachable shard

`assertAllReachable` only checks shard health once, at application startup; to protect every real query at runtime (e.g. against a shard that has become temporarily slow), attach `bulkhead`/`timeLimit` around the repository call itself:

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository; // an ordinary JpaRepository over shardDataSource

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        BulkheadPolicies.define("order-shard-query", new BulkheadPolicy(20, 50, 1500));
        TimeLimitPolicies.define("order-shard-query", new TimeLimitPolicy(2000));
    }

    public Order findOrder(long orderId) {
        return new Try<>(() -> orderRepository.findById(orderId).orElseThrow())
                .bulkhead("order-shard-query")
                .timeLimit("order-shard-query")
                .onError(e -> { throw new OrderLookupException("shard query failed for order " + orderId, e); })
                .get();
    }
}
```

### JValidation + RequestAccessControl + Page2 — a typical create/list endpoint

Three fully independent modules that are nonetheless commonly chained together in one real endpoint: first validate the input with `JValidation`, then strip unauthorized fields with `RequestAccessControl` before saving/returning, and finally use `page2` for listing:

```java
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Employee employee,
                                     @AuthenticationPrincipal AppUser currentUser,
                                     EmployeeRepository repo) {
        ValidationResult validation = Validator.of(employee)
                .field(Employee::getMobile).required().regex(Patterns.MOBILE)
                .field(Employee::getSalary).number().positive()
                .validate();
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(validation.errors());
        }

        // Throws DeniedException here if the current role isn't allowed to set salary
        Employee restricted = RequestAccessControl.apply(currentUser.getRole(), employee, "CREATE");
        return ResponseEntity.ok(repo.save(restricted));
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, Object> gridParams,
                                     @AuthenticationPrincipal AppUser currentUser,
                                     EntityManager entityManager) {
        Map<String, Object> result = new PageDataEntity(entityManager)
                .searchAndSortOn("firstName", "lastName", "mobile")
                .getResult(Employee.class, gridParams);

        return new PageDataResultFilter<Employee>(result)
                .remove("nationalCode")
                .empty("salary") // only authorized roles see the salary; here it's always blanked as an example
                .getResult();
    }
}
```

### Page2 + JReport — exporting a filtered/searched dataset straight to PDF

`page2`'s `getResult()` map already contains the filtered, sorted `"rows"` list — `DynamicReport`/`EntityTemplateReport` just need that list handed to them instead of a live SQL query:

```java
@RestController
@RequestMapping("/employees")
public class EmployeeReportController {

    @GetMapping("/export")
    public void exportFilteredEmployees(@RequestParam Map<String, Object> gridParams,
                                         HttpServletResponse response,
                                         EntityManager entityManager) throws Exception {
        Map<String, Object> result = new PageDataEntity(entityManager)
                .searchAndSortOn("firstName", "lastName", "department")
                .getResult(Employee.class, gridParams);

        @SuppressWarnings("unchecked")
        List<Employee> employees = (List<Employee>) result.get("rows");

        // Convert entities to the Map<String,Object> rows EntityTemplateReport expects
        List<Map<String, Object>> rows = employees.stream()
                .map(e -> Map.<String, Object>of("name", e.getFullName(), "department", e.getDepartment()))
                .toList();

        EntityTemplateReport.generateToResponse(
                response, "/reports/employee-list.jrxml", ReportType.XLSX, Map.of(), rows);
    }
}
```

### JCrux + ResiCord — bounding concurrent access to a shared container object

Because `JCruxServer` has no built-in rate limiting (see the security notice under [JCrux](#jcrux)), wrap client calls in a `bulkhead` so a burst of concurrent callers cannot exhaust the server's reflection/IO resources:

```java
@Service
public class SharedConfigClient {

    private final JCruxClient jCruxClient;

    public SharedConfigClient(JCruxClient jCruxClient) {
        this.jCruxClient = jCruxClient;
        BulkheadPolicies.define("jcrux-access", new BulkheadPolicy(5, 10, 1000));
    }

    public Object readConfigField(String objectId, String fieldName) {
        return new Try<>(() -> jCruxClient.getField(objectId, fieldName))
                .bulkhead("jcrux-access")
                .timeLimit(1000)
                .onError(e -> { throw new ConfigAccessException(e); })
                .get();
    }
}
```

---

## Frontend (Vue / Nuxt)

The project's frontend is a Vue 3 (`<script setup>`) module — independent of the Java backend, but designed to work directly with `page2`'s output format. In addition to the `DataTable.vue` component, the project has **twenty composables** (`useXxx`), each encapsulating one self-contained browser capability. The table below lists all of them; the description and example for each follow.

| Composable | Responsibility |
|---|---|
| [`useSecurity`](#usesecurity--client-side-xss-sanitization) | HTML sanitization against XSS (built on DOMPurify) |
| [`useCodeEditor`](#usecodeeditor--an-embeddable-code-editor) | An embeddable code editor with syntax highlighting, formatting, file export |
| [`useRichText`](#userichtext--an-embeddable-rich-text-wysiwyg-editor) | An embeddable rich text (WYSIWYG) editor |
| [`useDate`](#usedate--binding-a-persian-calendar-to-an-inputmask) | Binds a Persian calendar mask (IMask + jalaali-js) to an input, with a valid date range |
| [`useNumber`](#usenumber--the-frontend-counterpart-of-jnumber) | The frontend counterpart of `JNumber` — number-to-words conversion and a live numeric input mask |
| [`useValidation`](#usevalidation--form-validation-driven-by-dom-attributes) | Form validation driven by the input elements' own attributes (not a separate object) |
| [`useDynamicForm`](#usedynamicform--a-form-with-a-variable-number-of-rows) | Managing a form with a variable number of rows (add/remove row) |
| [`useTree`](#usetree--a-server-driven-lazy-load-tree) | A tree component with lazy loading from the server, mirroring the philosophy of `DataTable.vue` |
| [`useFileLoader`](#usefileloader--dynamic-scriptcss-loading-with-reference-counting) | Dynamically importing a JS/CSS file with reference counting (used internally by `useCodeEditor`/`useRichText` and similar) |
| [`useLocalStorage`](#uselocalstorage--a-safe-layer-over-localstorage) | A safe wrapper around `localStorage` (degrades gracefully if the browser doesn't support it) |
| [`useFullscreen`](#usefullscreen--browser-fullscreen-mode) | Entering/exiting the browser's fullscreen mode for an element |
| [`useKeyListener`](#usekeylistener--keyboard-shortcuts) | Defining keyboard shortcuts (e.g. Ctrl+S) |
| [`useQR`](#useqr--qr-code-generation) | Generating and downloading a QR code |
| [`useDigitalSignature`](#usedigitalsignature--a-handwritten-signature-on-canvas) | Capturing a handwritten signature on a `<canvas>` |
| [`useScreenRecorder`](#usescreenrecorder--screen-recording) | Recording the browser screen (Screen Capture API) |
| [`useSpeechRecognitionAPI`](#usespeechrecognitionapi--speech-to-text) | Speech-to-text conversion (Web Speech API) |
| [`useTranslatorAPI`](#usetranslatorapi--text-translation) | Translating text between two languages |
| [`useLocationAPI`](#uselocationapi--geolocation--reverse-geocoding) | Getting the user's coordinates and converting them to an address/city/country |
| [`useNetworkAPI`](#usenetworkapi--the-users-networkip-information) | The user's IP/network information (country, ISP, speed, ping) |
| [`useOperatingSystemNotification`](#useoperatingsystemnotification--os-level-notifications) | OS-level notifications (Notification API) |

### `useSecurity` — client-side XSS sanitization

The frontend counterpart of the backend's `XssProtector`, built on DOMPurify:

```js
const security = useSecurity()

security.protectStrictXSS(userInput)   // strips every tag and attribute - for plain text
security.protectXSS(userInput, { ALLOWED_TAGS: ['b', 'i'] })  // sanitizes while allowing a few specific tags
```

**Rule:** anywhere user-supplied data is rendered as HTML (rather than plain text), it must first pass through `protectStrictXSS`/`protectXSS`. Sanitization is not applied automatically.

### `useCodeEditor` — an embeddable code editor

Turns a container element into a full code editor: syntax highlighting, autocomplete, and a formatter, without pulling any editor library into the main bundle (it's loaded on demand, only when this composable is actually used).

Full API: `editor` (the raw underlying editor instance ref, for anything not covered below), `init(editorId, options)`, `get()`, `set(code)`, `readOnly()`, `readWrite()`, `reformatCode()`, `downloadCode(fileName)`, `destroy()`. `options` accepts `language` (default `"java"`), `theme` (default `"ace/theme/monokai"`), `fontSize` (default `16`), plus `snippets`/`autocompletes` for registering custom autocomplete entries.

```js
const codeEditor = useCodeEditor()

onMounted(async () => {
  // init(elementId, options) attaches the editor to the DOM element with that id and turns
  // it into a live code editor - options control the syntax language and color theme.
  await codeEditor.init('code-editor-div', { language: 'java', theme: 'ace/theme/monokai' })
  codeEditor.set('public class Main {}') // pre-fill with existing content, e.g. loaded from an API
})

function onSave() {
  const currentCode = codeEditor.get()  // read the editor's current content as a plain string
  codeEditor.reformatCode()              // auto-format/indent the code in place
  codeEditor.downloadCode('Main.java')   // trigger a browser download of the current content
}

function onApprove() { codeEditor.readOnly() }   // lock the editor once a review is submitted
function onReopen() { codeEditor.readWrite() }   // unlock it again

onBeforeUnmount(() => codeEditor.destroy()) // release the editor instance when the component unmounts
```

### `useRichText` — an embeddable rich text (WYSIWYG) editor

Turns a container element into a formatting toolbar + editable area, for content the user is meant to format visually rather than write as raw HTML - a comment box, a product description, an article body. **Its output must never be rendered directly (`v-html`) without first passing through `useSecurity`** — this editor does not sanitize against XSS on its own.

Full API: `editor`, `init(editorId, options)`, `getHtml()`, `getText()`, `setHtml(html)`, `clear()`, `exportHTML()`, `exportText()`, `setReadOnly(boolean)`, `destroy()`.

```js
const richText = useRichText()
const security = useSecurity()

onMounted(() => richText.init('article-body-editor'))

function onSubmit() {
  const safeHtml = security.protectXSS(richText.getHtml(), { ALLOWED_TAGS: ['p', 'b', 'i', 'a', 'ul', 'li'] })
  saveArticle({ body: safeHtml })
}

function onLockAfterPublish() { richText.setReadOnly(true) }
function onDiscard() { richText.clear() }
onBeforeUnmount(() => richText.destroy())
```

### `useDate` — turning a plain text input into a live Persian-calendar date box

`setupDateBox` is the core of this composable: given the DOM id of an ordinary `<input>` element, it attaches a live input mask that turns that plain text field into a guided Persian-calendar date box. From that point on, the input behaves differently from a normal text field:

- As the user types, the field only accepts digits in the `YYYY/MM/DD` shape and auto-inserts the `/` separators — there's no way to type something that isn't structurally a date.
- Each keystroke is validated against the actual Persian calendar: a month can't exceed 12, and a day can't exceed how many days that specific month actually has in that specific year (so e.g. day 30 of Esfand is rejected in a non-leap year but accepted in a leap year).
- The range arguments define an allowed date **window** — any date the user types that falls before the "from" date or after the "to" date is rejected as invalid, without needing any validation code on your end.
- An optional `onChange` callback fires every time the field settles on a complete, valid date (internally, on the mask's `accept` event) — called with `''` while the field is incomplete/invalid, and the formatted `"YYYY/MM/DD"` string once it's valid. This is how a component reacts to the date live, without polling the input's `.value` itself.
- An optional `initialValue` pre-fills the box as soon as it's set up — either an explicit Persian date string (`"1403/06/15"`), or the literal string `"today"`, which resolves to the current Persian date automatically.

Full signature: `setupDateBox(inputId, fromYear, fromMonth, fromDay, toYear, toMonth, toDay, onChange, initialValue)`. Both `onChange` and `initialValue` are optional and can be omitted entirely if not needed.

The remaining helpers are read-only conversions, independent of any specific input element — and their real signatures take separators explicitly, not just a bare date string: `getPersianDate(dateString, inputSeparator, outputSeparator)` and `getGregorianDate(dateString, inputSeparator, outputSeparator)` convert a date string from one calendar to the other, splitting the input on `inputSeparator` and joining the result on `outputSeparator` — which lets the same helper handle a `"2024-09-05"` (dash-separated) input just as easily as a `"2024/09/05"` (slash-separated) one. `getGregorianNowDate()` takes no arguments and returns today's date as a dash-separated Gregorian string.

```js
const { setupDateBox, getPersianDate, getGregorianDate, getGregorianNowDate } = useDate()

onMounted(() => {
  // Turns the plain <input id="birth-date-input"> into a live Persian date box, pre-filled
  // with a specific date. Arguments: inputId, fromYear/Month/Day, toYear/Month/Day, onChange,
  // initialValue - the user can only enter a date between 1300/01/01 and 1403/12/29 inclusive.
  setupDateBox('birth-date-input', 1300, 1, 1, 1403, 12, 29,
    (value) => { birthDateIsComplete.value = value !== '' },
    '1370/01/01')

  // A second, independent date box, pre-filled with "today" instead of a fixed date, and
  // with no onChange callback (pass undefined to skip it while still supplying initialValue).
  setupDateBox('delivery-date-input', 1403, 1, 1, 1405, 12, 29, undefined, 'today')
})

function onSubmit() {
  // The input's raw .value is still just the text the mask produced, e.g. "1403/06/15" -
  // getGregorianDate converts that Persian-calendar string into the Gregorian equivalent,
  // ready to send to a backend that stores dates in the Gregorian calendar. Both the input
  // and output separators are explicit, since neither side is assumed.
  const birthDateInput = document.getElementById('birth-date-input')
  const gregorianBirthDate = getGregorianDate(birthDateInput.value, '/', '-')
  submitForm({ birthDate: gregorianBirthDate })
}

function onLoadExistingRecord(gregorianDateFromApi) {
  // The reverse direction: converting a Gregorian date coming back from an API (dash-separated)
  // into a Persian, slash-separated string to display - e.g. to re-fill a date box from saved data.
  const persian = getPersianDate(gregorianDateFromApi, '-', '/')
  document.getElementById('birth-date-input').value = persian
}
```

### `useNumber` — turning a plain text input into a live formatted number box

Just like `useDate`'s `setupDateBox`, the four `setup*NumberBox` functions each take the DOM id of an ordinary `<input>` element and attach a live mask to it, turning it from a plain text field into a guided numeric box. Once attached:

- Non-digit characters can't be typed at all — the field structurally cannot contain anything but a number.
- Thousands separators (commas) are inserted automatically as the user types, so `1234567` is displayed as `1,234,567` live, without the user having to type the commas themselves or the application reformatting it after the fact.
- Which of the four setup functions is used controls two independent choices: whether decimal input is allowed at all, and whether the digits displayed to the user are Latin or Persian.
- All four take a `maxLength` for the integer part (defaulting to 36 digits if omitted), and an optional `onChange` callback that fires on every edit (including a paste), called with the current *raw, unformatted* value (e.g. `"1234567"`, not `"1,234,567"`) — the right value to read if the application needs the number as the user types, rather than waiting for form submission.
- The two decimal-capable variants (`setupDoubleNumberBox`, `setupPersianDoubleNumberBox`) additionally take a `decimalPlaces` argument, capping how many digits are kept after the decimal point.
- **Each setup function returns a cleanup function** — calling it removes the input/paste listeners it attached. This is meant to be called from `onBeforeUnmount`, the same way `destroy()` is used on the editor/signature composables, so a component that sets up a number box doesn't leave listeners attached after it's torn down.

Full signatures: `setupNumberBox(inputId, maxLength, onChange)` / `setupPersianNumberBox(inputId, maxLength, onChange)` for integers; `setupDoubleNumberBox(inputId, maxLength, decimalPlaces, onChange)` / `setupPersianDoubleNumberBox(inputId, maxLength, decimalPlaces, onChange)` for decimals. Every parameter after `inputId` is optional.

| Setup function | Decimal input? | Digit script shown |
|---|---|---|
| `setupNumberBox(inputId, maxLength?, onChange?)` | No (integers only) | Latin (`0-9`) |
| `setupPersianNumberBox(inputId, maxLength?, onChange?)` | No (integers only) | Persian (`۰-۹`) |
| `setupDoubleNumberBox(inputId, maxLength?, decimalPlaces?, onChange?)` | Yes | Latin |
| `setupPersianDoubleNumberBox(inputId, maxLength?, decimalPlaces?, onChange?)` | Yes | Persian |

Beyond the four setup functions, the same conversion/formatting logic `JNumber` exposes on the backend is available directly as plain functions, for formatting a value you already have (not tied to any input element) or converting a formatted string back before sending it to an API.

```js
const {
  setupNumberBox, setupPersianNumberBox, setupDoubleNumberBox, setupPersianDoubleNumberBox,
  getPersianWords, getPersianNumber, getEnglishNumber, getEnglishNumberWithoutCommas
} = useNumber()

let unbindQuantityBox, unbindPriceBox

onMounted(() => {
  // A stock quantity field: whole numbers only, capped at 6 digits, Latin digits (English
  // admin panel). onChange keeps a reactive ref in sync with the raw numeric value live,
  // e.g. to validate against available stock or update a computed total as the user types.
  unbindQuantityBox = setupNumberBox('stock-quantity-input', 6, (raw) => {
    quantity.value = Number(raw)
  })

  // A unit price field: decimals meaningful (cents), capped at 2 decimal places, Persian
  // digits to match a Persian-facing storefront.
  unbindPriceBox = setupPersianDoubleNumberBox('unit-price-input', 10, 2, (raw) => {
    unitPrice.value = Number(raw)
  })
})

// Detach the listeners setupNumberBox/setupDoubleNumberBox attached, the same way the
// editor composables' destroy() releases their resources - important if this component
// can be unmounted while the page stays alive (e.g. inside a modal or a tab panel).
onBeforeUnmount(() => {
  unbindQuantityBox?.()
  unbindPriceBox?.()
})

function onSubmit() {
  // The input's raw value is already comma-formatted text, e.g. "12,500.00" - strip the
  // formatting back out before parsing it as an actual number to send to the backend.
  const priceInput = document.getElementById('unit-price-input')
  const plainNumber = getEnglishNumberWithoutCommas(priceInput.value)
  submitForm({ unitPrice: parseFloat(plainNumber) })
}

function renderInvoiceTotal(totalAmount) {
  // Formatting a value you already have, unrelated to any specific input element -
  // e.g. displaying a computed total, or spelling it out for a printed document.
  const displayValue = getPersianNumber(String(totalAmount))       // "۱۲,۵۰۰"
  const spelledOut = getPersianWords(String(totalAmount))           // spelled out in Persian words
  return { displayValue, spelledOut }
}
```

### `useValidation` — form validation driven by DOM attributes

Unlike `jvalidation` on the backend (which operates on a Java object), this composable reads a `<form>`'s fields directly, via the input elements' own attributes (e.g. `data-*`) — intended for simple client-side checks before a request is submitted to the server.

Full API: `state` (a reactive object holding `errors`), `clear()`, `addError(message)` (push a custom error manually), `addPattern(key, regex, shouldNormalize?)` (register a named pattern usable by an input's own attributes), `validateForm(formId)`, `getFormData(formId)`.

```js
const validation = useValidation()

validation.addPattern('mobile', /^09\d{9}$/)

function onSubmit() {
  validation.clear()
  const isValid = validation.validateForm('registration-form')
  if (!isValid) {
    validation.state.errors.forEach(msg => toast.add({ title: msg, color: 'error' }))
    return
  }

  submitForm(validation.getFormData('registration-form'))
}

function onCustomCrossFieldCheck(passwordEl, confirmEl) {
  if (passwordEl.value !== confirmEl.value) {
    validation.addError('Passwords do not match')
  }
}
```

> This is client-side validation only, and it is **not a substitute** for `jvalidation`/`Validator` on the backend — the backend must always re-validate, since client-side checks can be bypassed.

### `useDynamicForm` — a form with a variable number of rows

For forms such as "invoice line items", where the user can add or remove any number of rows.

```js
const dynamicForm = useDynamicForm(() => ({ productName: '', quantity: 1, price: 0 }))

dynamicForm.showForm()       // creates one empty row
dynamicForm.addRow()         // adds another row
dynamicForm.removeRow(rowId)

function onSubmit() {
  const invoiceLines = dynamicForm.getAllValues()
  saveInvoice(invoiceLines)
}
```

### `useTree` — a server-driven, lazy-load tree

Mirrors the philosophy of `DataTable.vue`: it talks to a URL on its own and fetches each node's children only when that node is expanded (lazy loading), along with a right-click context menu per node.

Full API: reactive state — `expandedKeys`, `treeData`, `menu`/`menuX`/`menuY` (context-menu position), `selectedItem`, `visibleActions`, `isBusy` — and methods — `init()`, `reload()`, `reset()`, `changeReload(nodeId)`/`addReload(nodeId)` (mark a node, or add a node, to be re-fetched from the server), `refreshNode(nodeId)`, `purgeSubtree(nodeId)` (drop a node's cached children so they are re-fetched next time it's expanded), `findNodeById(nodeId)`.

```vue
<template>
  <TreeView v-bind="tree" />
  <button @click="tree.refreshNode(selectedCategoryId)">Refresh this branch</button>
  <button @click="tree.purgeSubtree(selectedCategoryId)">Force-reload this branch's children</button>
</template>

<script setup>
const tree = useTree({
  listUrl: `${API_BASE}/categories/tree?`,
  parentIdParam: 'parentId',
  fields: { id: 'categoryId', label: 'categoryName', hasChildren: 'hasChildren' },
  actions: [{ key: 'delete', label: 'Delete' }],
  onNodeClick: (node) => router.push(`/categories/${node.categoryId}`),
  onError: (err) => console.error(err)
})

onMounted(() => tree.init())

function onCategoryRenamed(categoryId) {
  tree.changeReload(categoryId) // re-fetch just that node's label from the server
}
</script>
```

### `useFileLoader` — dynamic script/css loading with reference counting

The shared infrastructure that `useCodeEditor` and `useRichText` are built on: loads a JS/CSS file exactly once (even if several components request it at the same time), tracks a reference count, and removes it once the last consumer is gone.

Full API: `importJavaScriptSourceFile(src)`, `importStyleSheetFile(href)`, `removeJavaScriptSourceFile(src)`, `removeStyleSheetFile(href)`, `removeAllDynamicFiles()`.

```js
const fileLoader = useFileLoader()

onMounted(async () => {
  await fileLoader.importStyleSheetFile('/plugins/chart/chart.min.css')
  await fileLoader.importJavaScriptSourceFile('/plugins/chart/chart.min.js')
})

onBeforeUnmount(() => {
  fileLoader.removeJavaScriptSourceFile('/plugins/chart/chart.min.js')
  fileLoader.removeStyleSheetFile('/plugins/chart/chart.min.css')
})

// e.g. on full app teardown / logout, to release every dynamically loaded plugin at once:
function onLogout() { fileLoader.removeAllDynamicFiles() }
```

### `useLocalStorage` — a safe layer over localStorage

Behaves like plain `localStorage`, but does not fail silently (or noisily) if the browser (or a private-browsing mode) refuses to allow writes.

Full API: `persist(key, value)`, `findByKey(key)`, `removeByKey(key)`, `getKeys()`.

```js
const storage = useLocalStorage()

storage.persist('token', jwtToken)
const token = storage.findByKey('token')
storage.removeByKey('token')

const allStoredKeys = storage.getKeys() // e.g. to clean up stale app-specific entries on version upgrades
```

### `useFullscreen` — browser fullscreen mode

Full API: `init(elementId)`, `open()`, `close()`, `toggle()`.

```js
const fullscreen = useFullscreen()

onMounted(() => fullscreen.init('video-player-container'))
function onFullscreenClick() { fullscreen.toggle() }
function onExitButtonClick() { fullscreen.close() } // e.g. a custom on-screen "exit fullscreen" button
```

### `useKeyListener` — keyboard shortcuts

`setupKeyBoardShortcuts` takes a plain object whose keys are the shortcut combination, written as `Ctrl+`/`Shift+`/`Alt+` prefixes (in that order, if combined) followed by the key itself (a single-character key is matched case-insensitively; a named key like `Escape` or `Enter` is matched as-is) — and whose values are the handler function to call, invoked with the triggering `KeyboardEvent`. It returns a cleanup function that removes the listener.

```js
const { setupKeyBoardShortcuts } = useKeyListener()

let unbindShortcuts

onMounted(() => {
  unbindShortcuts = setupKeyBoardShortcuts({
    'Ctrl+s': (event) => { event.preventDefault(); onSave() },
    'Escape': () => onCancel(),
    'Ctrl+Shift+d': () => onDuplicate()
  })
})

onBeforeUnmount(() => unbindShortcuts?.())
```

### `useQR` — QR code generation

`render(tagId, url)` draws a QR code into the element with the given id; `download(tagId)` re-reads the QR code already rendered into that same element and triggers a browser download of it (as `QR.png` — the download name isn't customizable).

```js
const qr = useQR()

onMounted(() => qr.render('qr-container', `https://example.com/invoice/${invoiceId}`))

// download() takes the SAME container id passed to render() - not a filename - and looks
// for the QR code already drawn inside it.
function onDownloadQr() { qr.download('qr-container') }
```

### `useDigitalSignature` — a handwritten signature on canvas

`save(fileName)` triggers a browser download of the signature directly (as a PNG) — it does not return the image data to the caller, so capturing the signature for an API call means reading the canvas separately rather than using `save()`'s return value.

Full API: `init(canvasId)`, `clear()`, `save(fileName?)` (defaults to `"signature.png"`), `destroy()`.

```js
const signature = useDigitalSignature()

onMounted(() => signature.init('signature-canvas'))
function onClear() { signature.clear() }

// save() downloads the signature as a file directly; it has no return value.
function onDownloadSignature() { signature.save('contract-signature.png') }

// To send the signature to a backend instead of downloading it, read the canvas directly.
function onSubmitContract() {
  const canvas = document.getElementById('signature-canvas')
  const signatureImageBase64 = canvas.toDataURL('image/png')
  submitContract({ signature: signatureImageBase64 })
}

onBeforeUnmount(() => signature.destroy())
```

### `useScreenRecorder` — screen recording

`init` takes the id of the `<video>` element to preview the recording in, plus two flags for whether to capture microphone and/or system audio alongside the screen. `stop` takes the download file name.

Full API: `init(videoId, captureMic?, captureSystemAudio?)` (both flags default to `false`), `start()`, `stop(fileName?)` (defaults to `"screen-record.webm"`).

```js
const recorder = useScreenRecorder()

async function onStartRecording() {
  // Capture the screen plus the microphone, previewed live in <video id="recording-preview">.
  await recorder.init('recording-preview', true, false)
  recorder.start()
}
function onStopRecording() { recorder.stop('support-call-recording.webm') }
```

### `useSpeechRecognitionAPI` — speech-to-text

`init` takes three callbacks rather than an options object: one called with each recognized result, one called on an error, and one called when recognition ends.

Full API: `init(onResult, onError, onEnd)`, `start()`, `stop()`, `clear()`, `exportFile(fileName?)`.

```js
const speech = useSpeechRecognitionAPI()

onMounted(() => {
  speech.init(
    (transcript) => { commentBox.value = transcript },     // onResult
    (err) => toast.add({ title: 'Speech recognition failed', description: err.message }), // onError
    () => { isListening.value = false }                    // onEnd
  )
})

function onMicClick() {
  isListening.value = true
  speech.start()
}
function onMicStop() { speech.stop() }
function onRestart() { speech.clear() }
function onExportTranscript() { speech.exportFile('call-transcript.txt') }
```

### `useTranslatorAPI` — text translation

```js
const { translate } = useTranslatorAPI()

const englishText = await translate('Hello, world', 'fa', 'en')
```

### `useLocationAPI` — geolocation + reverse geocoding

`setupLocationListener`'s success callback receives `latitude` and `longitude` as two separate numbers (not a combined `coords` object) — and every lookup function (`getAddress`, `getCountry`, `getCity`, `getPostCode`) takes those same two numbers as separate arguments too, not a single object.

Full API: `setupLocationListener(onSuccess, onError)` where `onSuccess(latitude, longitude)`; `getAddress(latitude, longitude)`, `getCountry(latitude, longitude)`, `getCity(latitude, longitude)`, `getPostCode(latitude, longitude)` — all `async`.

```js
const location = useLocationAPI()

location.setupLocationListener(
  async (latitude, longitude) => {
    shippingForm.city = await location.getCity(latitude, longitude)
    shippingForm.country = await location.getCountry(latitude, longitude)
    shippingForm.postCode = await location.getPostCode(latitude, longitude)
    shippingForm.fullAddress = await location.getAddress(latitude, longitude)
  },
  (err) => toast.add({ title: 'Location access was denied' })
)
```

### `useNetworkAPI` — the user's network/IP information

Full API: `isInternetConnected()`, `getIP()`, `getContinent()`, `getCountry()`, `getCity()`, `getISP()`, `getOrganization()`, `getDomain()`, `getEmoji()` (the requesting country's flag emoji), `getLatitude()`/`getLongitude()`, `getPing()`, `getDownloadSpeed()`, `getNetworkType()`, `getNetworkGeneration()` (e.g. `"4g"`/`"5g"` on mobile connections).

```js
const network = useNetworkAPI()

if (network.isInternetConnected()) {
  const [country, city, isp, downloadSpeed, generation] = await Promise.all([
    network.getCountry(),
    network.getCity(),
    network.getISP(),
    network.getDownloadSpeed(),
    network.getNetworkGeneration()
  ])
  // e.g. warn the user before uploading a large file on a slow/metered connection
}
```

### `useOperatingSystemNotification` — OS-level notifications

`showNotification` takes three positional arguments — a title, a plain-text message body, and an optional website URL to open (in a new tab, focusing the window) when the notification is clicked — rather than an options object. It requests notification permission itself if not already granted, and resolves to `false` (rather than throwing) if the browser doesn't support notifications, permission is denied, or showing the notification otherwise fails.

Full API: `showNotification(title, message, website?)`, `async`, returns `true`/`false`.

```js
const { showNotification } = useOperatingSystemNotification()

const wasShown = await showNotification(
  'New order',
  'A new order has been placed',
  `${APP_BASE}/orders/${orderId}` // opened in a new tab if the user clicks the notification
)
if (!wasShown) {
  // e.g. permission was denied, or the browser doesn't support notifications - fall back
  // to an in-app toast instead.
  toast.add({ title: 'New order', description: 'A new order has been placed' })
}
```

### `DataTable.vue` — a server-driven table with pagination/sort/search

A general-purpose component that connects to a URL via `fetch` on its own and handles pagination, sorting, and search internally.

```vue
<template>
  <DataTable
    :url="`${API_BASE}/users/grid?`"
    :columns="columns"
    :page-size="20"
    default-sort="name"
    row-key="id"
    @error="onLoadError"
  />
</template>

<script setup>
const security = useSecurity()

const columns = [
  { field: 'name', label: 'Name', sortable: true },
  { field: 'email', label: 'Email', sortable: true },
  {
    field: 'organization',
    label: 'Organization',
    sortable: true,
    sortName: 'organization.name',
    processor: (row) => `<b>${security.protectStrictXSS(row.organization.name)}</b>`,
    render: true // since the processor returns HTML, it must already be sanitized via protectStrictXSS
  }
]

function onLoadError(err) {
  console.error(err)
}
</script>
```

### Real integration: `DataTable.vue` (frontend) + `page2` (backend), with zero extra mapping code

An important detail found directly in the code: the query parameters `DataTable.vue` constructs (`page`, `rows`, `q`, `sort`, `order`) are exactly the keys that `PageDataEntity.getResult(Class, gridParams)`/`PageDataList.getResult(...)` expect. In other words, connecting the two requires no extra mapping layer at all — the controller can take `@RequestParam Map<String, Object> gridParams` directly and hand it straight to `page2`:

```java
// --- Backend: Spring Boot ---
@RestController
@RequestMapping("/users")
public class UserGridController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/grid")
    public Map<String, Object> grid(@RequestParam Map<String, Object> gridParams) {
        // gridParams is exactly what DataTable.vue sent: page, rows, q, sort, order
        return new PageDataEntity(entityManager)
                .searchAndSortOn("name", "email", "organization.name")
                .getResult(User.class, gridParams);
        // output: {total, rows, page, size, search} - exactly the shape DataTable.vue expects
    }
}
```

```vue
<!-- --- Frontend: as soon as `url` points to this endpoint, you're done --- -->
<DataTable
  :url="`${API_BASE}/users/grid?`"
  :columns="[
    { field: 'name', label: 'Name', sortable: true },
    { field: 'email', label: 'Email', sortable: true }
  ]"
  default-sort="name"
/>
```

If you also want sensitive fields removed or masked before they ever reach the frontend (e.g. salary or a national ID number), add a `PageDataResultFilter` between `page2` and the controller's return value — the same pattern shown in the "JValidation + RequestAccessControl + Page2" section above; the frontend requires no changes at all, since the shape of the output stays the same.

---

## Testing Conventions

If you contribute to any `org.j2os.platform.*` module, match its existing test style rather than introducing JUnit/TestNG: **none of the backend modules use a JUnit-family test framework.** Each module instead ships a plain class with a `main()` method (e.g. `JCruxTest`, `JShardTest`, `ResicordTest`) that runs a series of checks with a small local `Check`/assertion helper (`isTrue`, `equals`, `throwsException`, and similar), printing a `[PASS]`/`[FAIL]` line per check and a summary at the end. Some modules also provide a single `RunAllMains`-style entry point that runs every test/example class in the package sequentially. There is no Maven Surefire/Failsafe integration for these — you run the `main()` class directly (from your IDE or `java -cp ...`) and read its console output.

This has a few practical implications:

- These tests aren't picked up by `mvn test` or by standard coverage tooling (JaCoCo); running one means running its `main()` class directly (from an IDE, or `java -cp ...`) and reading its console output.
- A CI pipeline expecting a JUnit XML report needs a small adapter to surface results from these modules alongside JUnit-based ones.
- JUnit-based tests can be added on top of these modules in a consumer application without conflict — the two styles coexist in the same build; the modules themselves simply don't use JUnit internally.

---

## Full API Reference

This section walks through the complete public surface of every module, one at a time. For each module: a short explanation of what it's for and when to reach for it, a table listing every public method, and a complete, realistic example exercising the whole table — not just the one or two methods a typical use case needs. Parameter-by-parameter and exception documentation lives in each class's own Javadoc; the goal here is to see the whole API in context, in one place, so nothing has to be guessed at from a method name alone.

### JBalancer — `JRoundRobinBalancer`

`JRoundRobinBalancer` solves one specific problem: an application talks to a backend service that has more than one instance, and calls should be spread evenly across them without any external load balancer in front. It is a plain in-process singleton (reached via `getInstance()`), so registration typically happens once, at startup, and lookups happen on every outgoing call.

| Method | Description |
|---|---|
| `configurationResource(String resourceId, List<String> urls)` | Registers or replaces the URL list for a resource id |
| `removeResource(String resourceId)` | Removes a registered resource |
| `getResourceUrl(String resourceId)` | Returns the next URL in rotation; throws `ResourceNotFoundException` if unregistered |

```java
// Registered once, e.g. in a @PostConstruct method, when the application starts.
JRoundRobinBalancer.getInstance().configurationResource("email-service", List.of(
        "http://email-node-1:8080",
        "http://email-node-2:8080",
        "http://email-node-3:8080"));

// Called every time an outgoing request needs an address. Each call advances the rotation,
// so three consecutive calls here return node-1, then node-2, then node-3, then wrap back to node-1.
try {
    String url = JRoundRobinBalancer.getInstance().getResourceUrl("email-service");
    sendEmailRequest(url, message);
} catch (ResourceNotFoundException e) {
    // Thrown if "email-service" was never registered (e.g. a typo in the resource id,
    // or this code ran before the @PostConstruct registration above).
    throw new IllegalStateException("email-service is not configured", e);
}

// If the set of nodes changes at runtime (e.g. one node is decommissioned), re-register
// under the same id - this replaces the URL list and resets rotation back to the first URL.
JRoundRobinBalancer.getInstance().configurationResource("email-service", List.of(
        "http://email-node-1:8080", "http://email-node-2:8080"));

// Fully de-register a resource, e.g. when a feature backed by it is disabled.
JRoundRobinBalancer.getInstance().removeResource("email-service");
```

### JCrux — `JCruxClient`

JCrux exists for one narrow situation: several JVM processes need shared, live access to a small number of Java objects — configuration, a small in-memory registry, a singleton service — without standing up a database or a cache cluster for it (see [JCrux](#jcrux) above for the full design rationale). Every method below throws a checked `Exception` on failure — an invalid token, an unknown object id, or a failure inside the invoked method/constructor itself all surface the same way, so production code should catch and handle that explicitly rather than letting it propagate as a raw `Exception`.

| Method | Description |
|---|---|
| `connect(String ip, String service, String token)` | Looks up the remote container via RMI and stores the token |
| `create(String classAddress, Object... params)` | Instantiates a class server-side and stores it; returns its id |
| `put(Object object)` | Stores an already-constructed object; returns its id |
| `get(String id)` | Retrieves a stored object |
| `invoke(String id, String methodName, Object... params)` | Invokes a method (including non-public ones) on a stored object |
| `setField(String id, String attribute, Object value)` / `getField(String id, String attribute)` | Writes/reads a field (including non-public ones) on a stored object |
| `remove(String id)` | Removes a stored object |
| `list()` | Lists every stored object's id and runtime class |
| `save()` / `load()` | Serializes the whole container to disk / restores it from disk |
| `setAutoSave(boolean)` | Enables/disables auto-save after every mutating call |
| `freeMemory()` / `totalMemory()` / `uptime()` / `jvmThreadCount()` / `lastSaveTime()` / `saveCount()` / `containerFilePath()` | Server introspection helpers |

```java
public class SharedConfigDemo {

    public void demonstrateFullLifecycle() {
        JCruxClient client = new JCruxClient();

        try {
            // Step 1: connect. This does an RMI registry lookup and remembers the token
            // for every subsequent call made through this client instance.
            client.connect("localhost", "config-container", System.getenv("JCRUX_TOKEN"));

            // Step 2: create a server-side object by fully-qualified class name and constructor
            // arguments. The server instantiates it and hands back a generated id.
            String configId = client.create("org.j2os.examples.AppConfig", "production", 30);

            // Step 3: read it back, call a method on it, and mutate one of its fields directly.
            Object storedConfig = client.get(configId);
            Object currentTimeout = client.invoke(configId, "getTimeoutSeconds");
            client.setField(configId, "timeoutSeconds", 60);
            Object updatedTimeout = client.getField(configId, "timeoutSeconds");
            System.out.println("timeout changed from " + currentTimeout + " to " + updatedTimeout);

            // put(...) is the alternative to create(...): use it when the object already
            // exists locally (e.g. it was built with a builder, or came from elsewhere)
            // rather than needing the server to construct it from scratch.
            AppConfig stagingConfig = new AppConfig("staging", 15);
            String stagingId = client.put(stagingConfig);

            // list() enumerates every object currently stored in the container - useful for
            // an admin/debug view, or for cleaning up objects an application no longer needs.
            for (JCruxObject stored : client.list()) {
                System.out.println(stored.getObjectId() + " -> " + stored.getObjectType());
            }

            // Remove the object we no longer need.
            client.remove(stagingId);

            // Persistence: with autosave off (the default unless the server was constructed
            // with autosave=true), changes only live in memory until save() is called explicitly.
            client.setAutoSave(false);
            client.save();  // write the whole container to disk right now
            client.load();  // discard any further in-memory changes and reload from disk

            // Server introspection - useful for a health-check or admin dashboard.
            System.out.printf("uptime=%dms, threads=%d, freeMemory=%d, totalMemory=%d, "
                            + "saveCount=%d, lastSave=%d, file=%s%n",
                    client.uptime(), client.jvmThreadCount(), client.freeMemory(), client.totalMemory(),
                    client.saveCount(), client.lastSaveTime(), client.containerFilePath());

        } catch (Exception e) {
            // Every JCruxClient call can fail this way: invalid token, unknown id, unresolved
            // class, or an exception thrown by the invoked constructor/method/field access.
            throw new RuntimeException("JCrux operation failed", e);
        }
    }
}
```

### JFlow — `JFlowClient`

JFlow is a full BPMS: it manages long-running business processes (leave requests, approval chains, order fulfillment workflows) defined as BPMN diagrams, tracks which step each running instance is on, and hands out the tasks that are waiting for a human to act on them (see [JFlow](#jflow) above for the full design rationale). Every method below throws a checked `Exception` for the same reasons as JCrux.

| Method | Description |
|---|---|
| `startProcessAndGetProcessInstanceId(processDefinitionKey[, variables])` | Starts a new process instance |
| `forceSignalByProcessInstanceId(processInstanceId[, variables])` | Resumes the single waiting execution of a process instance |
| `signalByTaskId(taskId[, variables])` | Completes a user task |
| `moveByProcessInstanceId(processInstanceId, targetActivityId)` | Force-moves a process instance to a different activity |
| `getAllOpenTasks()` / `getOpenTasksByAssignee(user)` / `getOpenTasksByProcessInstanceId(id)` / `getOpenTasksByProcessDefinitionKey(key)` | Task queries |
| `getProcessDiagramByProcessInstanceId(id)` / `getProcessDiagramByProcessDefinitionKey(key)` | Renders a PNG diagram |
| `getVariablesByProcessInstanceId(id)` / `getVariableByProcessInstanceId(id, name)` | Reads process variables |
| `setVariablesByProcessInstanceId(id, variables)` / `setVariableByProcessInstanceId(id, name, value)` | Writes process variables |
| `suspendByProcessDefinitionKey(key)` / `activateByProcessDefinitionKey(key)` | Suspends/reactivates a process definition |
| `deployFromSourceAndGetProcessDefinitionKey(resourceName, bpmnXml)` / `deployFromFileAndGetProcessDefinitionKey(filePath)` | Deploys a BPMN definition |
| `getActiveProcessDefinitionKeys()` / `getSuspendedProcessDefinitionKeys()` | Lists process definitions by status |
| `getRemote()` | Returns the raw `JFlowRemote` RMI stub, for advanced/direct use |

`JFlowServer` additionally exposes `getProcessEngine()` (the underlying Flowable `ProcessEngine`, for anything not covered by `JFlowClient`) and `close()` (unexports the RMI object and closes the engine — always call this on application shutdown; see the [minimal JFlow example](#jflow) above for where it's wired in).

```java
public class LeaveRequestWorkflowDemo {

    public void runFullLeaveRequestLifecycle(JFlowClient client) throws Exception {
        // Deployment: upload the BPMN definition once (typically at application startup,
        // or via an admin action when a new/updated workflow is rolled out).
        String processDefinitionKey = client.deployFromFileAndGetProcessDefinitionKey("leave-request.bpmn20.xml");

        // Starting a new instance: an employee submits a leave request, which becomes one
        // running "process instance" that JFlow tracks through every step of the workflow.
        String instanceId = client.startProcessAndGetProcessInstanceId(
                processDefinitionKey, Map.of("employeeId", 42, "requestedDays", 3));

        // The workflow's first step waits for a manager to act. Find that task by assignee,
        // by process instance, by process definition, or list every open task in the system.
        List<JFlowTask> managerInbox = client.getOpenTasksByAssignee("manager-1");
        List<JFlowTask> tasksOnThisRequest = client.getOpenTasksByProcessInstanceId(instanceId);
        List<JFlowTask> tasksOnThisWorkflow = client.getOpenTasksByProcessDefinitionKey(processDefinitionKey);
        List<JFlowTask> everyOpenTaskInTheSystem = client.getAllOpenTasks();

        // The manager approves: complete their task, optionally with output variables.
        JFlowTask approvalTask = tasksOnThisRequest.get(0);
        client.signalByTaskId(approvalTask.getTaskId(), Map.of("approved", true));

        // Reading and writing process variables directly - useful for showing request status
        // in a UI, or for an administrator correcting a mistaken value mid-flow.
        client.setVariableByProcessInstanceId(instanceId, "priority", "high");
        client.setVariablesByProcessInstanceId(instanceId, Map.of("escalated", true, "notifiedHR", true));
        Object priority = client.getVariableByProcessInstanceId(instanceId, "priority");
        Map<String, Object> allVariables = client.getVariablesByProcessInstanceId(instanceId);

        // forceSignalByProcessInstanceId resumes a waiting execution that isn't backed by a
        // user task (e.g. a timer or a signal event) - the workflow equivalent of "unstick this".
        client.forceSignalByProcessInstanceId(instanceId, Map.of("skippedReview", true));

        // moveByProcessInstanceId force-jumps a running instance to a different activity -
        // an escape hatch for correcting a process that's stuck or was routed incorrectly.
        client.moveByProcessInstanceId(instanceId, "hrReviewActivity");

        // Rendering a diagram - the running instance's diagram highlights its current activity;
        // the definition's diagram is the plain, unhighlighted process map.
        byte[] instanceDiagramPng = client.getProcessDiagramByProcessInstanceId(instanceId);
        byte[] definitionDiagramPng = client.getProcessDiagramByProcessDefinitionKey(processDefinitionKey);

        // Suspending a definition stops new instances from starting (e.g. during a policy
        // change) without affecting instances already in flight; activating reverses it.
        client.suspendByProcessDefinitionKey(processDefinitionKey);
        client.activateByProcessDefinitionKey(processDefinitionKey);

        // Listing every deployed workflow, split by whether new instances can start.
        List<String> activeWorkflows = client.getActiveProcessDefinitionKeys();
        List<String> suspendedWorkflows = client.getSuspendedProcessDefinitionKeys();
    }
}
```

### JReport

JReport turns tabular data into a downloadable PDF, DOCX, or XLSX file. It offers three entry points depending on where the data comes from and how much control the report layout needs: `DynamicReport` builds a report purely from a SQL query and a column list, with no separate design file; `TemplateReport` and `EntityTemplateReport` both fill a `.jrxml` file designed visually beforehand, differing only in where the data comes from (a live database query vs. rows already fetched in Java). Each entry point can write to an HTTP response (`generateToResponse`, for a download endpoint) or straight to a file on disk (`generateToFile`).

| Class / Method | Description |
|---|---|
| `DynamicReport.generateToResponse(...)` / `.generateToFile(...)` | Builds a report from a raw SQL query + column list, writes to an `HttpServletResponse` or a file |
| `EntityTemplateReport.generateToResponse(...)` / `.generateToFile(...)` | Fills a `.jrxml` template with an already-fetched `List<Map<String, Object>>` — takes **no** `Connection` |
| `TemplateReport.generateToResponse(...)` / `.generateToFile(...)` | Fills a `.jrxml` template whose query is embedded in the template itself — takes a live `Connection` |
| `ReportType.PDF` / `.DOCX` / `.XLSX` | The three supported export formats |

```java
public class ReportGenerationDemo {

    public void generateEveryReportKind(Connection connection) throws IOException {
        // DynamicReport: no design file needed at all - just a query, the columns to show,
        // and the header label for each column. Good for quick, code-only reports.
        DynamicReport.generateToFile(
                "stock-report.xlsx", connection, "stock-report", ReportType.XLSX, "Current Stock Levels",
                "SELECT sku, name, quantity FROM stock WHERE quantity < 100",
                List.of("sku", "name", "quantity"),
                List.of("SKU", "Product Name", "Quantity"));

        // EntityTemplateReport: the visual layout lives in invoice.jrxml (branding, positioning,
        // fonts, page structure), but the data is handed to it already fetched - here, from JPA.
        List<Map<String, Object>> invoiceLineItems = List.of(
                Map.of("item", "Widget", "quantity", 3, "unitPrice", 9.99),
                Map.of("item", "Gadget", "quantity", 1, "unitPrice", 49.99));
        EntityTemplateReport.generateToFile(
                "invoice-1001.pdf", "/reports/invoice.jrxml", ReportType.PDF,
                Map.of("orderId", 1001L, "customerName", "Ada Lovelace"),
                invoiceLineItems);

        // TemplateReport: same kind of visual layout file, but this variant's SQL query is
        // embedded inside the .jrxml itself and is executed against the live Connection.
        TemplateReport.generateToFile(
                "invoice-1001-live.pdf", connection, "/reports/invoice-with-embedded-query.jrxml",
                ReportType.PDF, Map.of("orderId", 1001L));
    }

    // The generateToResponse(...) overloads take the exact same arguments, plus an
    // HttpServletResponse in place of a file path - see the JReport Spring Boot example above
    // for a full @RestController using this inside a download endpoint.
}
```

### JSecurity - Access Control

Two classes solve two related problems: `RequestAccessControl` restricts what a caller can do to an object *before* it's saved or returned — nulling out specific fields per role, or denying an operation outright — while `ResponseAccessControl` restricts fields on an already-shaped API response (typically a `page2` result). Both use the same three-part key: a `scope` (usually a role), a target class, and an action name. Restrictions are registered once (usually at startup) and looked up on every call to `apply(...)`.

| Method | Description |
|---|---|
| `RequestAccessControl.registerFieldLimitation(scope, Class/className, field, action)` | Registers a field to be nulled out for a scope+class+action |
| `RequestAccessControl.registerActionDenial(scope, Class/className, action)` | Registers a full denial for a scope+class+action |
| `RequestAccessControl.unregisterFieldLimitation(...)` / `.unregisterActionDenial(...)` | Removes a previously registered restriction |
| `RequestAccessControl.apply(scope, target, action)` | Returns a restricted shallow copy of `target`; throws `DeniedException` if denied |
| `RequestAccessControl.apply(scope, target, oldTarget, action)` | Same, but restricted fields fall back to `oldTarget`'s value instead of `null` |
| `ResponseAccessControl.apply(page2ResultMap, restrictedFields, restrictedFieldAction)` | Restricts fields directly on a `page2` `getResult()` map |
| `ResponseAccessControl.apply(List<?> rows, restrictedFields, restrictedFieldAction)` | Restricts fields on a plain row list |
| `ResponseAccessControl.apply(Object entity, restrictedFields, restrictedFieldAction)` | Restricts fields on a single object |
| `ResponseAccessControl.EMPTY` / `.REMOVE` | The two supported `restrictedFieldAction` values (blank the field vs. drop it entirely) |

```java
public class AccessControlDemo {

    // Typically registered once at startup, in a @PostConstruct method.
    public void registerRestrictions() {
        // An EMPLOYEE role can view a User, but never sees the salary field.
        RequestAccessControl.registerFieldLimitation("EMPLOYEE", User.class, "salary", "VIEW");
        // An EMPLOYEE role is never allowed to delete a User at all.
        RequestAccessControl.registerActionDenial("EMPLOYEE", User.class, "DELETE");
    }

    // Used on every request, e.g. inside a controller method.
    public User getUserForCurrentRole(String currentRole, User user) {
        // apply(...) returns a shallow copy with restricted fields nulled - the original
        // `user` object is never mutated, so it's still safe to reuse or cache elsewhere.
        return RequestAccessControl.apply(currentRole, user, "VIEW");
    }

    public User updateUserForCurrentRole(String currentRole, User incoming, User existing) {
        // The 4-argument overload is for partial updates: a field the current role isn't
        // allowed to change falls back to `existing`'s value instead of being wiped to null,
        // so a restricted PATCH request can't accidentally erase a field it never touched.
        try {
            return RequestAccessControl.apply(currentRole, incoming, existing, "UPDATE");
        } catch (RequestAccessControl.DeniedException denied) {
            // Thrown when the whole action - not just a field - was registered as denied
            // via registerActionDenial(...).
            throw new AccessDeniedException("Role " + currentRole + " may not update users", denied);
        }
    }

    public void removeRestriction() {
        // Reverses a specific registration - the other restrictions for the same
        // scope+class+action (if any) are left untouched.
        RequestAccessControl.unregisterFieldLimitation("EMPLOYEE", User.class, "salary", "VIEW");
        RequestAccessControl.unregisterActionDenial("EMPLOYEE", User.class, "DELETE");
    }

    // ResponseAccessControl operates on shapes rather than a single typed object - useful right
    // before a page2 result, a raw row list, or a single entity goes out over an API response.
    public Map<String, Object> restrictGridResponse(Map<String, Object> page2Result) {
        return new ResponseAccessControl()
                .apply(page2Result, List.of("salary", "nationalCode"), ResponseAccessControl.EMPTY);
        // EMPTY blanks the field's value but keeps the key present in the output;
        // REMOVE (used below) drops the key from the map entirely.
    }

    public List<Map<String, Object>> restrictRawRows(List<Map<String, Object>> rows) {
        return new ResponseAccessControl().apply(rows, List.of("internalNotes"), ResponseAccessControl.REMOVE);
    }

    public Map<String, Object> restrictSingleEntity(User user) {
        return new ResponseAccessControl().apply(user, List.of("passwordHash"), ResponseAccessControl.REMOVE);
    }
}
```

### JSecurity - Cryptography — `Cryptography`

A single static utility class covering the cryptographic operations a typical application needs: hashing (both plain fingerprinting and slow, salted password hashing), symmetric (AES) and asymmetric (RSA) encryption, and cryptographically secure random values. Every method is `static`, so nothing needs to be instantiated — call `Cryptography.methodName(...)` directly.

| Category | Methods |
|---|---|
| Encoding | `encodeBase64`/`decodeBase64`, `encodeBase64URL`/`decodeBase64URL` |
| Hashing | `hashByMD5`, `hashBySHA2_256`, `hashBySHA2_512`, `hashBySHA3_256`, `hashBySHA3_512`, `hashFileByMD5`, `hashFileBySHA2_256`, `hashFileBySHA2_512` |
| Password hashing | `hashByBCrypt`/`checkByBCrypt`, `hashByArgon2`/`checkByArgon2`, `hashByPBKDF2`/`checkByPBKDF2` (all `check*` comparisons are constant-time) |
| AES | `generateAESKey(bits)`, `encryptStringByAES`/`decryptStringByAES` (key- or password-based overloads), `encryptFileByAES`/`decryptFileByAES` (with an optional `durable` fsync flag) |
| RSA | `generateRSAKeys(keySize)`, `encryptStringByRSA`/`decryptStringByRSA`, `encodeRSAPublicKey`/`encodeRSAPrivateKey`, `decodeRSAPublicKey`/`decodeRSAPrivateKey` |
| Random values | `uuid()`, `randomInt(...)`, `randomDouble(...)`, `randomBoolean()`, `randomAlphaString(length)`, `randomAlphaNumericString(length)` — all backed by `SecureRandom` |

```java
public class CryptographyDemo {

    // --- Encoding: for turning binary data (files, keys, ciphertext) into text-safe strings ---
    public String encodeFileForJsonTransport(byte[] fileBytes) {
        return Cryptography.encodeBase64(fileBytes);
    }
    public byte[] decodeFileFromJsonTransport(String base64) {
        return Cryptography.decodeBase64(base64);
    }

    // --- Plain hashing: fingerprints for integrity checks, not for anything secret ---
    public boolean fileMatchesKnownHash(String filePath, String expectedSha256) {
        return Cryptography.hashFileBySHA2_256(filePath).equals(expectedSha256);
    }

    // --- Password hashing: pick ONE algorithm and use it consistently across the application ---
    public String hashNewUserPassword(String rawPassword) {
        return Cryptography.hashByArgon2(rawPassword); // or hashByBCrypt / hashByPBKDF2
    }
    public boolean verifyLoginPassword(String rawPassword, String storedHash) {
        return Cryptography.checkByArgon2(rawPassword, storedHash); // constant-time comparison internally
    }

    // --- AES: symmetric encryption, either with a managed SecretKey or a plain password ---
    public String encryptNoteWithManagedKey(String note, SecretKey key) {
        return Cryptography.encryptStringByAES(note, key);
    }
    public String encryptNoteWithPassword(String note, String password) {
        // No key management required - the key is derived from the password internally.
        return Cryptography.encryptStringByAES(note, password);
    }
    public void encryptExportFileDurably(String filePath, String password) {
        Cryptography.encryptFileByAES(filePath, password, true); // true = fsync before returning
    }

    // --- RSA: asymmetric encryption, e.g. for a payload only one specific party can decrypt ---
    public KeyPair issueNewKeyPair() {
        return Cryptography.generateRSAKeys(2048);
    }
    public byte[] encryptForRecipient(String message, PublicKey recipientPublicKey) {
        return Cryptography.encryptStringByRSA(message, recipientPublicKey);
    }
    public String decryptOwnMessage(byte[] cipherBytes, PrivateKey ownPrivateKey) {
        return Cryptography.decryptStringByRSA(cipherBytes, ownPrivateKey);
    }
    public String exportPublicKeyForSharing(PublicKey publicKey) {
        return Cryptography.encodeRSAPublicKey(publicKey); // Base64 text, safe to store/transmit
    }

    // --- Secure random values: for tokens, OTPs, IDs - never java.util.Random for these ---
    public String newSessionId() { return Cryptography.uuid(); }
    public String newSixDigitOtp() { return Cryptography.randomAlphaNumericString(6); }
    public int rollDie() { return Cryptography.randomInt(1, 6); }
}
```

### JSecurity - XSS Protection — `XssProtector`

`XssProtector` sanitizes user-supplied text before it is ever rendered as HTML in a browser, using one of three strategies depending on what the field is for.

| Method | Description |
|---|---|
| `toRichText(String value)` | Sanitizes, keeping a curated set of safe formatting tags |
| `toPlainText(String value)` | Strips all markup down to plain text |
| `toDisplayHtml(String value)` | HTML-escapes the input so markup renders literally instead of being interpreted |

```java
public class XssProtectionDemo {

    private final XssProtector protector = new XssProtector();

    // Use toRichText for content the user is expected to format - a comment box, an article
    // body written in a WYSIWYG editor - where <b>, <i>, links, and similar should survive.
    public String sanitizeArticleBody(String rawHtmlFromEditor) {
        return protector.toRichText(rawHtmlFromEditor);
        // "<b>Hello</b><script>alert(1)</script>" -> "<b>Hello</b>"
    }

    // Use toPlainText when no formatting should survive at all - a person's display name,
    // a search query, a short free-text field with no legitimate reason to contain markup.
    public String sanitizeDisplayName(String rawInput) {
        return protector.toPlainText(rawInput);
        // "<b>Ada</b> <i>Lovelace</i>" -> "Ada Lovelace"
    }

    // Use toDisplayHtml when you want to show someone their own raw markup as text, rather
    // than render it - e.g. an admin screen previewing what a user typed into a template field.
    public String showRawMarkupSafely(String rawInput) {
        return protector.toDisplayHtml(rawInput);
        // "<b>bold</b>" -> "&lt;b&gt;bold&lt;/b&gt;" (displayed literally, never executed)
    }
}
```

### JShard

The core building block for a sharded application is `JShardDataSourceProvider.builder()`: it takes a description of your shards and tables and returns a plain `javax.sql.DataSource`. Everything downstream of that (`JShardConnectionConfig`, `JShardTableConfig`) is just configuration for the builder; `JShardDataSource` and `JShardRouter` are the two lower-level pieces exposed on the built `DataSource` itself, for the rarer cases where you need to inspect routing or shut things down explicitly rather than letting a framework manage the `DataSource`'s lifecycle.

| Class / Method | Description |
|---|---|
| `JShardConnectionConfig.of(driver, jdbcUrl, username, password, poolSize)` / `.builder()` | Describes one physical database connection |
| `JShardConnectionConfig` getters | `getDriverClassName`, `getJdbcUrl`, `getUsername`, `getPassword`, `getPoolSize`, `getConnectionTimeoutMs`, `getIdleTimeoutMs`, `getMaxLifetimeMs`, `getMinimumIdle`, `getValidationTimeoutMs`, `getKeepaliveTimeMs` |
| `JShardConnectionConfig.Builder` | `.driverClassName(...)`, `.jdbcUrl(...)`, `.username(...)`, `.password(...)`, `.poolSize(...)`, `.connectionTimeoutMs(...)`, `.idleTimeoutMs(...)`, `.maxLifetimeMs(...)`, `.minimumIdle(...)`, `.validationTimeoutMs(...)`, `.keepaliveTimeMs(...)`, `.build()` |
| `JShardTableConfig(name, shardingColumn)` | Declares a sharded table and the column that determines routing |
| `JShardDataSourceProvider.assertAllReachable(shards)` | Checks connectivity to every configured shard; throws if any is unreachable |
| `JShardDataSourceProvider.builder().shards(...).table(...)/.tables(...).showSql(...).build()` | Builds the `DataSource` |
| `JShardDataSourceProvider.builder().shardGroups(...)` / `.shards(Map<String, List<...>>)` | Bulk-registers shards, each with an optional list of replicas |
| `JShardDataSource` (implements `javax.sql.DataSource`) | Standard `DataSource` methods (`getConnection()`, `unwrap`, `isWrapperFor`, `getLogWriter`/`setLogWriter`, `getLoginTimeout`/`setLoginTimeout`, `getParentLogger`), plus `close()` (shuts down every shard's connection pool) and `getRouter()` (returns the `JShardRouter`, for computing a record's shard without going through JPA) |
| `JShardRouter.getShardKey(...)` | Computes which shard a given sharding-column value would route to |

```java
public class ShardSetupDemo {

    public DataSource buildDataSourceStepByStep() throws Exception {
        // JShardConnectionConfig describes one physical database - built either via the
        // compact of(...) factory (for the common case) or the Builder (for extra tuning
        // like connection timeouts and pool sizing).
        JShardConnectionConfig shardAPrimary = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://db1:5432/app",
                "app", System.getenv("DB_PASSWORD"), 10);

        JShardConnectionConfig shardBPrimary = JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl("jdbc:postgresql://db2:5432/app")
                .username("app")
                .password(System.getenv("DB_PASSWORD"))
                .poolSize(10)
                .connectionTimeoutMs(5000)
                .idleTimeoutMs(600000)
                .maxLifetimeMs(1800000)
                .minimumIdle(2)
                .build();

        // Reading a config back - useful for logging/diagnostics before the DataSource is built.
        System.out.println("shard-b will connect to " + shardBPrimary.getJdbcUrl()
                + " with pool size " + shardBPrimary.getPoolSize());

        Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
        shards.put("shard-a", List.of(shardAPrimary));
        shards.put("shard-b", List.of(shardBPrimary));

        // assertAllReachable fails fast, with a clear error, if any shard is unreachable -
        // far better to discover a misconfigured connection here than on the first real query.
        JShardDataSourceProvider.assertAllReachable(shards);

        DataSource dataSource = JShardDataSourceProvider.builder()
                .shards(shards)
                .table("order_tbl", "order_id")     // JShardTableConfig(name, shardingColumn), built inline
                .table("payment_tbl", "order_id")   // a second sharded table, routed by the same column
                .showSql(true)                       // log every generated SQL statement, for debugging
                .build();

        return dataSource;
    }

    public void inspectAndShutdown(DataSource dataSource) {
        // JShardDataSource implements plain javax.sql.DataSource, so it can be cast to it
        // when you need the two extra methods it adds beyond the standard interface.
        JShardDataSource jShardDataSource = (JShardDataSource) dataSource;

        // getRouter().getShardKey(...) answers "which shard would this row live on?" without
        // running any query at all - handy for logging, debugging, or a manual data-repair script.
        String shardForOrder1001 = jShardDataSource.getRouter().getShardKey("order_tbl", 1001L);
        System.out.println("order 1001 lives on: " + shardForOrder1001);

        // close() releases every shard's connection pool. Call this once, on application
        // shutdown - Spring Boot examples elsewhere in this document wire it in automatically
        // via bean lifecycle; call it manually only in a standalone (non-Spring) program.
        jShardDataSource.close();
    }
}
```

### JUtil - Date — `JDate`

`JDate` is a single mutable object representing one calendar date, always available in both the Persian and Gregorian calendars at once — every getter/setter on it just reads or rewrites the same underlying date, whichever calendar you address it through.

| Method | Description |
|---|---|
| `new JDate()` / `new JDate(year, month, day)` | Constructs today's date / a specific Persian date |
| `getPersianDate()` / `getGregorianDate()` | String representations in each calendar |
| `getPersianYear()` / `getPersianMonth()` / `getPersianDay()` | The three Persian-calendar components as integers |
| `getGregorianYear()` / `getGregorianMonth()` / `getGregorianDay()` | The three Gregorian-calendar components as integers |
| `setPersianDate(year, month, day)` / `setGregorianDate(year, month, day)` | Rebases this instance to a specific date, in either calendar |
| `nextDay([n])` / `previousDay([n])` / `addDays(n)` | Advances/rewinds by one day, `n` days, or a signed offset |
| `getDayOfWeek()` | The day of week as an integer |
| `getWeekDayName()` / `getPersianWeekDayName()` | The day of week name, in English or Persian |
| `isLeap()` / `isLeap(year)` | Whether this instance's year — or an arbitrary given year — is a Persian leap year |
| `getGregorianDateTimestamp(String persianDateString)` | Parses a Persian date string directly into a `java.sql.Timestamp` |
| `getPersianDateString(Timestamp)` | The reverse: formats a `Timestamp` as a Persian date string |
| `toString()` | A combined weekday + both-calendar representation |

```java
public class JDateDemo {

    public void demonstrateEveryMethod() {
        // Constructing a specific Persian date - the Gregorian side is derived automatically.
        JDate date = new JDate(1403, 6, 15);

        // Reading the same date through both calendars.
        System.out.println(date.getPersianDate());   // e.g. "1403/06/15"
        System.out.println(date.getGregorianDate());  // the equivalent Gregorian date, e.g. "2024/09/05"

        // Reading individual components, when you need year/month/day as numbers rather
        // than a formatted string (e.g. to populate three separate <select> dropdowns).
        System.out.printf("Persian: %d/%d/%d%n", date.getPersianYear(), date.getPersianMonth(), date.getPersianDay());
        System.out.printf("Gregorian: %d-%d-%d%n", date.getGregorianYear(), date.getGregorianMonth(), date.getGregorianDay());

        // Weekday information.
        System.out.println(date.getWeekDayName());          // e.g. "Thursday"
        System.out.println(date.getPersianWeekDayName());    // the same weekday, spelled in Persian
        System.out.println("day of week index: " + date.getDayOfWeek());

        // Leap year checks - both for this instance's own year, and for an arbitrary year.
        System.out.println(date.isLeap() ? "1403 is a leap year" : "1403 is not a leap year");
        System.out.println(date.isLeap(1404) ? "1404 is a leap year" : "1404 is not a leap year");

        // Mutating this instance in place - useful when iterating day by day, e.g. building
        // a calendar view or computing a due date N business days from now.
        date.nextDay();       // advance by exactly one day
        date.nextDay(10);     // advance by ten days
        date.previousDay(3);  // rewind by three days
        date.addDays(-5);     // a signed offset - identical to previousDay(5) here

        // Rebasing this same instance to a completely different date, in either calendar.
        date.setPersianDate(1404, 1, 1);      // Persian new year
        date.setGregorianDate(2025, 12, 25);  // an arbitrary Gregorian date

        // Converting between a Persian date string and a java.sql.Timestamp - useful right
        // at the JDBC boundary, when a database column stores a Timestamp but the UI works
        // in Persian date strings.
        Timestamp timestamp = date.getGregorianDateTimestamp("1403/06/15");
        String persianAgain = date.getPersianDateString(timestamp);
        System.out.println(persianAgain); // "1403/06/15" again, round-tripped through a Timestamp

        // toString() gives a single combined representation, handy for quick logging.
        System.out.println(date);
    }
}
```

### JUtil - JSON — `JSon`

A thin convenience layer over Jackson for the two things needed most often: full serialization/deserialization to a Java type, and reading a single field out of a JSON string without paying for a full object deserialization (useful for inspecting an incoming webhook payload before deciding which class to deserialize it into, for example).

| Method | Description |
|---|---|
| `read(String json, Class<T>)` | Deserializes JSON into an object |
| `write(Object)` | Serializes an object to a JSON string |
| `readFieldAsText(String json, String... path)` | Reads one (possibly nested) field's value as plain text, without a full deserialization |
| `readFieldAsString(String json, String... path)` | Reads one (possibly nested) field as its raw JSON string representation (preserves quoting/structure, unlike `readFieldAsText`) |
| `readFieldArrayAsText(String json, int index, String fieldName)` / `readFieldArrayAsString(String json, int index, String fieldName)` | Same as above, but reads one field from the element at `index` of a JSON array |

```java
public class JSonDemo {

    public void demonstrateEveryMethod() {
        String incomingPayload = """
                {
                  "type": "user.updated",
                  "user": { "name": "Ada Lovelace", "roles": ["admin", "editor"] }
                }
                """;

        // Full deserialization - use this once you know which Java type the payload maps to.
        WebhookEvent event = JSon.read(incomingPayload, WebhookEvent.class);

        // Full serialization - the reverse direction, e.g. for an outgoing request body.
        String serializedAgain = JSon.write(event);

        // readFieldAsText - a lightweight peek at one field, without deserializing the whole
        // payload into a Java object. Ideal for branching logic before you know the full shape.
        String eventType = JSon.readFieldAsText(incomingPayload, "type");
        if ("user.updated".equals(eventType)) {
            String userName = JSon.readFieldAsText(incomingPayload, "user", "name"); // "Ada Lovelace"
            handleUserUpdatedEvent(userName);
        }

        // readFieldAsString - like readFieldAsText, but returns the field's own JSON
        // representation rather than its plain text value (matters for non-string fields,
        // e.g. a nested object or array, where "plain text" wouldn't be well-defined).
        String rawUserObject = JSon.readFieldAsString(incomingPayload, "user");

        // readFieldArrayAsText / readFieldArrayAsString - for payloads whose top level is a
        // JSON array of objects; read one field from the element at a given index.
        String bulkPayload = "[{\"name\": \"first\"}, {\"name\": \"second\"}]";
        String firstName = JSon.readFieldArrayAsText(bulkPayload, 0, "name");  // "first"
        String secondNameJson = JSon.readFieldArrayAsString(bulkPayload, 1, "name"); // "\"second\""
    }

    private void handleUserUpdatedEvent(String userName) {
        // ...
    }
}
```

### JUtil - Number — `JNumber`

Number formatting and number-to-words conversion for Persian-locale applications, plus the reverse operations. Every method is `static`.

| Method | Description |
|---|---|
| `getEnglishWords(numStr)` / `getPersianWords(numStr)` | Spells a number out in English/Persian words, including a decimal part |
| `getPersianNumber(str)` | Formats digits as Persian, with thousands separators |
| `getEnglishNumber(str)` | Formats digits as Latin, with thousands separators |
| `getEnglishNumberWithoutCommas(str)` | Strips separators back out of a formatted number |

```java
public class JNumberDemo {

    public void demonstrateEveryMethod() {
        // Spelling a number out in words - e.g. for the "amount in words" line on a printed
        // invoice or cheque, where digits alone aren't considered sufficiently formal/secure.
        System.out.println(JNumber.getEnglishWords("123.45"));  // "One Hundred Twenty Three Point Four Five"
        System.out.println(JNumber.getPersianWords("-45.6"));    // spelled out in Persian, negative sign included as a word

        // Formatting raw digits with thousands separators - e.g. displaying a price field.
        System.out.println(JNumber.getPersianNumber("1234567"));  // "۱,۲۳۴,۵۶۷"
        System.out.println(JNumber.getEnglishNumber("۱۲۳۴۵۶۷"));  // "1,234,567" (also accepts Persian-digit input)

        // Reversing formatting back to a plain numeric string, e.g. right before parsing a
        // formatted UI field back into a BigDecimal to send to the backend.
        String plain = JNumber.getEnglishNumberWithoutCommas("1,234,567"); // "1234567"
        BigDecimal amount = new BigDecimal(plain);
    }
}
```

### JValidation

`Validator.of(target)` starts a fluent chain: call `.field(getterReference)` once per field to validate, chain one or more rule methods after it, and finish with `.validate()`. Because fields are referenced by method reference rather than by string name, a typo or a rename that breaks a field reference is caught by the compiler, not at runtime.

| Class / Method | Description |
|---|---|
| `Validator.of(target)` | Starts a validation chain for an object |
| `.when(Predicate)` / `.endWhen()` | Conditionally applies the rules that follow |
| `.field(getterReference)` | Starts a rule chain for one field, referenced by a method reference (not a string name) |
| `.validate()` / `.validateOrThrow()` | Runs all rules; returns a `ValidationResult`, or throws `ValidationException` if invalid |
| `Field` rules | `required()`, `notBlank()`, `length`/`minLength`/`maxLength`/`lengthBetween`, `regex(String/Pattern)`, `contains`/`startsWith`/`endsWith`, `number()`, `min`/`max`/`positive`/`between(double,double)`, `digits(intDigits, fracDigits)`, `bool()`/`isTrue()`/`isFalse()`, `date()`/`persianDate()`, `past()`/`future()`, `before`/`after`/`between(String,String)`, `minimumAge(years)`, `notEmpty()`/`size`/`minSize`/`maxSize`, `unique()`, `equalTo(otherFieldGetter)`, `withMethod(BiPredicate, code, message)` |
| `Patterns` | Ready-made regexes: `MOBILE`, `NATIONAL_CODE`, `LEGAL_ENTITY_ID`, `POSTAL_CODE`, `LANDLINE_IR`, `SHEBA_NUMBER`, `CARD_NUMBER`, `LICENSE_PLATE_IR`, `EMAIL`, `URL`, `USERNAME`, `STRONG_PASSWORD`, `IPV4`, `IPV6`, `SLUG`, `UUID`, `BASE64`, `JWT`, `IBAN`, `HEX_COLOR` |
| `ValidationResult` | `isValid()`, `errorCount()`, `errors()` (a list of `Error`, each with `getField()`/`getCode()`/`getMessage()`/`getMessageKey()`/`getInvalidValue()`) |

```java
public class OrderValidationDemo {

    public ValidationResult validateNewOrder(Order order) {
        return Validator.of(order)
                // .when(...) scopes the rules that follow to only run when the condition
                // holds - here, an IBAN is only required for international orders.
                .when(o -> o.getType() == OrderType.INTERNATIONAL)
                    .field(Order::getIban).required().regex(Patterns.IBAN)
                .endWhen()

                // String rules: required/notBlank for presence, length rules for size,
                // regex for a custom or built-in pattern.
                .field(Order::getSku).required().notBlank().length(12)
                .field(Order::getCustomerMobile).required().regex(Patterns.MOBILE)

                // Numeric rules: number() first, then range/sign checks.
                .field(Order::getQuantity).number().positive().max(1000)
                .field(Order::getDiscountPercent).number().between(0, 100)
                .field(Order::getUnitPrice).number().digits(10, 2) // up to 10 integer digits, 2 decimal digits

                // Date rules, in either calendar: past()/future() relative to now, or an
                // explicit before/after/between comparison against another date string.
                .field(Order::getCreatedAt).date().past()
                .field(Order::getDeliveryDate).persianDate().future()
                .field(Order::getPromisedByDate).date().before("2026-12-31")

                // Collection rules: notEmpty/size/minSize/maxSize for a List/Set/array field.
                .field(Order::getLineItems).notEmpty().minSize(1).maxSize(50)

                // A custom rule via withMethod(...), for logic no built-in rule covers -
                // here, checking a promo code against an external service.
                .field(Order::getPromoCode).withMethod(
                        (o, code) -> code == null || promoService.isValid(code),
                        "INVALID_PROMO", "Unknown or expired promo code")

                // Cross-field comparison: this field must equal another field on the same object.
                .field(Order::getConfirmEmail).equalTo(Order::getEmail)

                .validate();
    }

    public void validateAndReactToResult(Order order) {
        ValidationResult result = validateNewOrder(order);

        if (!result.isValid()) {
            System.out.println(result.errorCount() + " validation error(s):");
            for (ValidationResult.Error error : result.errors()) {
                // Each Error exposes the field name, a machine-readable code (useful for
                // mapping to a translated message client-side), a human-readable message,
                // an optional message key, and the invalid value itself for logging/debugging.
                System.out.printf("  %s [%s]: %s (value was: %s)%n",
                        error.getField(), error.getCode(), error.getMessage(), error.getInvalidValue());
            }
            return;
        }

        saveOrder(order);
    }

    public void validateOrThrowExample(Order order) {
        // validateOrThrow() is a shortcut for code paths that treat any validation failure
        // as exceptional - it runs the same rules but throws ValidationException instead of
        // returning a ValidationResult you have to check yourself.
        Validator.of(order)
                .field(Order::getSku).required()
                .validateOrThrow();
    }

    private void saveOrder(Order order) { /* ... */ }
}
```

### Page2

Five classes cover the same filter/search/sort/paginate contract over five different data sources. `PageDataEntity` is the right default whenever the data is a JPA entity and the filters map cleanly onto its fields; reach for `PageDataJPQL`/`PageDataSQL` when the query needs a join, a computed column, or logic that doesn't map onto a single entity; and `PageDataList` when the data is already sitting in memory as a Java `List`. `PageDataResultFilter` is a separate, optional last step that reshapes any of the four result maps above before they leave the application (hiding, masking, or computing fields).

| Class | Constructor | `getResult(...)` signature |
|---|---|---|
| `PageDataEntity` | `(EntityManager)` | `.where`/`.and`/`.or`/`.searchAndSortOn(...)` then `.getResult(entityClass, gridParams)` |
| `PageDataJPQL` | `(EntityManager)` | `.searchAndSortOn(...)` then `.getResult(jpql, countJpql, entityAlias, entityClass, queryParams, gridParams)` |
| `PageDataSQL` | `(Connection)` | `.searchAndSortOn(...)` then `.getResult(sql, sqlParams, gridParams)` |
| `PageDataList` | *(no-arg)* | `.where`/`.and`/`.or`/`.searchAndSortOn(...)` then `.getResult(data, gridParams)` |
| `PageDataResultFilter<T>` | `(resultMap)` | `.remove`/`.empty`/`.mask`/`.put(...)` then `.getResult()` — callable only once per instance |

```java
public class Page2Demo {

    // PageDataEntity - the default choice for a straightforward JPA entity grid.
    public Map<String, Object> listUsers(EntityManager entityManager, Long orgId, Map<String, Object> gridParams) {
        return new PageDataEntity(entityManager)
                .where("organizationId", "=", orgId)          // a fixed security filter
                .and("active", "=", true)                     // combined with AND
                .searchAndSortOn("name", "email")              // the only fields sort/search may touch
                .getResult(User.class, gridParams);
    }

    // PageDataJPQL - for a query that needs a join across entities.
    public Map<String, Object> listOrdersWithCustomerJoin(EntityManager entityManager, Long orgId,
                                                            Map<String, Object> gridParams) {
        return new PageDataJPQL(entityManager)
                .searchAndSortOn("o.customerName", "o.status")
                .getResult(
                        "select o from Order o join o.customer c where c.organizationId = :orgId",
                        "select count(o) from Order o join o.customer c where c.organizationId = :orgId",
                        "o",                                    // the entity alias used in both queries above
                        Order.class,
                        Map.of("orgId", orgId),                // base query parameters, distinct from gridParams
                        gridParams);
    }

    // PageDataSQL - for querying a reporting view or table with no JPA entity mapped to it at all.
    public Map<String, Object> listFromReportingView(Connection connection, Long orgId,
                                                       Map<String, Object> gridParams) throws SQLException {
        return new PageDataSQL(connection)
                .searchAndSortOn("customer_name", "status")
                .getResult("select * from order_summary_view where org_id = :orgId",
                        Map.of("orgId", orgId), gridParams);
    }

    // PageDataList - for paginating a list already fully loaded in memory (e.g. the result
    // of an external API call, or a small reference dataset cached at startup).
    public Map<String, Object> listInMemoryUsers(List<User> allUsers, Map<String, Object> gridParams) {
        return new PageDataList()
                .where("active", "=", true)
                .searchAndSortOn("name", "email")
                .getResult(allUsers, gridParams);
    }

    // PageDataResultFilter - reshaping any of the four result maps above before it leaves
    // the application: remove a field entirely, blank it, mask it, or compute a new one.
    public Map<String, Object> shapeForApiResponse(Map<String, Object> rawResult) {
        return new PageDataResultFilter<User>(rawResult)
                .remove("passwordHash")                                          // drop entirely
                .mask("nationalCode")                                            // replace with "********"
                .put("fullName", u -> u.getFirstName() + " " + u.getLastName())  // add a computed field
                .put("ageGroup", Integer.class, (u, currentAge) ->               // transform an existing field
                        currentAge != null && currentAge >= 18 ? "adult" : "minor")
                .getResult(); // may only be called once per PageDataResultFilter instance
    }
}
```

### ResiCord

`Try<T>` wraps one unit of work and lets retry, a time limit, and bulkhead concurrency-bounding be attached to it in any combination, in a fluent chain ending in `.get()`. Each of the three resilience behaviors can be configured either inline (raw numbers, for a one-off call) or via a named policy registered once and reused everywhere (`RetryPolicies`, `TimeLimitPolicies`, `BulkheadPolicies`) — named policies are the better choice once more than one call site needs the same resilience behavior, since the configuration then lives in exactly one place.

| Class / Method | Description |
|---|---|
| `Try<T>(Block<T>)` / `new Try<T>().doWork(Block<T>)` | Wraps a unit of work |
| `.retry(maxAttempts, delayMillis)` / `.retry(policyName)` | Retries on failure |
| `.timeLimit(millis)` / `.timeLimit(policyName)` | Fails if the work exceeds a time budget |
| `.bulkhead(policyName)` | Bounds concurrency via a named thread pool + semaphore |
| `.onError(ErrorBlock<T>)` | Supplies a fallback value (or rethrows) on failure |
| `.get()` | Runs the configured chain and returns the result |
| `RetryPolicies` / `TimeLimitPolicies` | `.define(name, policy)`, `.get(name)`, `.listAll()`, `.remove(name)` — named, process-wide policy registries |
| `BulkheadPolicies` | `.define(name, policy)` (first-time setup), `.reconfigure(name, policy)` (resizes an existing pool in place), `.currentConfig(name)`, `.listAll()`, `.remove(name)`, and `.execute(name, Callable<T>)` — this last one runs a task under a named bulkhead directly, without going through `Try` |
| `RetryPolicy(maxAttempts, delayMillis)` / `BulkheadPolicy(maxConcurrentThreads, maxQueueSize, maxWaitMillis)` / `TimeLimitPolicy(millis)` | The policy value types themselves |

```java
public class ResiCordDemo {

    // --- Inline configuration: fine for a one-off call, or a quick prototype ---
    public String callWithInlineResilience() {
        return new Try<>(() -> callExternalService())
                .retry(3, 500)         // up to 3 attempts total, 500ms apart
                .timeLimit(2000)        // fail if a single attempt runs past 2 seconds
                .onError(e -> "fallback-value") // returned instead of throwing, if every attempt fails
                .get();
    }

    // --- Named policies: defined once (e.g. at startup), referenced everywhere by name ---
    public void defineSharedPolicies() {
        RetryPolicies.define("payment-gateway", new RetryPolicy(3, 500));
        TimeLimitPolicies.define("payment-gateway", new TimeLimitPolicy(2000));
        BulkheadPolicies.define("payment-gateway", new BulkheadPolicy(10, 20, 1000));
    }

    public String callWithNamedPolicies() {
        return new Try<>(() -> callExternalService())
                .retry("payment-gateway")
                .timeLimit("payment-gateway")
                .bulkhead("payment-gateway")
                .onError(e -> { throw new PaymentGatewayException(e); }) // rethrow, wrapped
                .get();
    }

    // --- Inspecting and managing named policies at runtime ---
    public void inspectAndManagePolicies() {
        RetryPolicy currentRetryPolicy = RetryPolicies.get("payment-gateway");
        Map<String, RetryPolicy> everyRetryPolicy = RetryPolicies.listAll();
        RetryPolicies.remove("payment-gateway"); // e.g. as part of a config reload

        // BulkheadPolicies has extra methods the other two don't: currentConfig(), reconfigure()
        // (resize an existing pool in place, without tearing it down), and execute() (run a task
        // under a named bulkhead directly, when you don't also need retry or a time limit).
        BulkheadPolicy currentBulkheadConfig = BulkheadPolicies.currentConfig("payment-gateway");
        BulkheadPolicies.reconfigure("payment-gateway", new BulkheadPolicy(20, 40, 2000)); // scale up
        String directResult = BulkheadPolicies.execute("payment-gateway", () -> callExternalService());
    }

    // --- doWork(...): building a reusable Try<T> instance rather than a one-shot lambda ---
    public String reusableTryExample() {
        Try<String> template = new Try<>();
        return template.doWork(() -> callExternalService()).retry(3, 500).get();
    }

    private String callExternalService() { /* ... */ return "result"; }
}
```

---

## Design Notes

A collection of behaviors and design choices that are useful to know about, gathered here in one place rather than scattered across each module's section.

**JBalancer.** Registration is a singleton, keyed by resource id: re-registering under an id already in use replaces the URL list and resets rotation to the first URL. Health of the registered URLs isn't monitored automatically — adding or removing a node from rotation (e.g. because it went down) is the calling application's responsibility.

**JCrux / JFlow.** Neither includes an audit log of its own, so an application that wants a full record of every call (who did what, and when) adds logging around its `JCruxClient`/`JFlowClient` calls itself.

**JShard.** `assertAllReachable` is a point-in-time check, typically run once at startup — for continuously monitoring shard health afterward, an application adds its own periodic check. Consistent hashing governs where new writes land; moving existing rows when the shard topology changes (adding/removing a shard) is a separate data-migration step, outside JShard's scope.

**JValidation.** A `Validator` instance is meant to be built fresh per validation call — its rule-chain state accumulates through the fluent calls, so a single instance isn't meant to be shared across concurrent validations. The library's own internal caches (field-name extraction, the date rules' shared "today" reference) are safe to use concurrently across separate `Validator` instances.

**Page2 — all five classes.** Each of `PageDataEntity`, `PageDataJPQL`, `PageDataList`, `PageDataSQL`, and `PageDataResultFilter` is meant to be instantiated fresh per request/query, the same way a query-builder object typically is. A few specific behaviors worth knowing: `sort` is a required key in `gridParams` (a missing value raises a clear error rather than defaulting silently); only fields registered via `searchAndSortOn(...)` participate in sorting or free-text search, so a `where`/`and`/`or` filter never becomes end-user-controllable by accident; `LIKE`/free-text search use SQL-style `%`/`_` wildcards, matched case-insensitively against the whole value; a comparison against an explicit `null` never matches any operator (mirroring SQL's own three-valued logic — `IS NULL`/`IS NOT NULL` is the way to test for nullness); `PageDataResultFilter.getResult()` is meant to be called once per instance; and the base `sql`/`jpql` string passed to `PageDataSQL`/`PageDataJPQL` is developer-authored query text, not itself a place for untrusted input — values are bound safely through parameters, the same as with any parameterized query.

**ResiCord.** A named `BulkheadPolicy`'s first `define(...)` establishes the pool; a later `define(...)` under the same name reconfigures that same pool in place (see [Dynamic reconfiguration at runtime](#dynamic-reconfiguration-at-runtime) above) rather than replacing it outright. The retry mechanism targets `Exception`s specifically — a JVM `Error` (such as `OutOfMemoryError`) is treated as non-recoverable and always propagates rather than being retried, on every code path.

**JSecurity - Access Control (`RequestAccessControl`).** Building a restricted copy relies on a no-arg constructor on the target class (public or not); Java `record` types aren't a fit for this, since a record's fields can't be reassigned after construction. The copy `apply(...)` produces is shallow: the target object itself, and any nested object that sits directly on a restricted field's path, get their own copies — other nested objects are shared by reference with the original, the same as with most shallow-copy utilities.

**Logging.** Coverage varies by module: `jreport`, part of `jshard`, and `RequestAccessControl` log via `slf4j`; other modules leave logging to the calling application, which is the natural place to add it when a full audit trail is wanted (particularly useful around JCrux/JFlow calls, given their token-gated model).

**`jshard` alongside a non-sharded `DataSource` in the same Spring Boot application.** Because `JShardDataSource` is itself a `DataSource` bean, Spring's default `DataSourceAutoConfiguration` steps aside once it sees one already defined. An application with both sharded and non-sharded entities defines both `DataSource` beans explicitly and separately, as shown in the [JShard](#jshard) Spring Boot example above.

**`Cryptography.checkByBCrypt`/`checkByPBKDF2`** compare hashes using `MessageDigest.isEqual` internally — the constant-time comparison a password-hash check should use. The same pattern is worth reusing anywhere else a token or secret is compared directly, rather than `String.equals`.

**`DataTable.vue`'s `render: true` columns** display whatever HTML a column's `processor` returns, so sanitizing that HTML (e.g. via `security.protectStrictXSS`) is the `processor`'s responsibility, the same way it would be for any component that accepts pre-rendered HTML as a prop. See the [`DataTable.vue`](#datatablevue--a-server-driven-table-with-paginationsortsearch) section above for the pattern the existing columns follow.

**`DataTable.vue`'s `Authorization` header block** ships commented out, as a ready-to-enable template for token-based auth. Applications whose API requires a bearer token enable that block (or otherwise attach the token, e.g. via a shared `fetch` wrapper); applications authenticating via a session cookie can leave it as is.

