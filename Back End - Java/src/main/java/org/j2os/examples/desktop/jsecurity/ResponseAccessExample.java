package org.j2os.examples.desktop.jsecurity;

import org.j2os.platform.jsecurity.access.ResponseAccessControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
    @PersistenceContext
    private EntityManager entityManager;
    //http://localhost:8082/getEmployeeList?page=1&rows=10&q=&sort=id&order=ASC
    @GetMapping("/getEmployeeList")
    public Object getEmployeeList(@RequestParam Map<String, Object> map) throws Exception {
        List<String> limitationList = List.of("department");
        PageDataEntity pageDataEntity = new PageDataEntity(entityManager);
        pageDataEntity.searchAndSortOn("id", "name", "department");
        var result = pageDataEntity.getResult(Employee.class, map);
        return ResponseAccessControl.apply(result, limitationList, ResponseAccessControl.REMOVE);
    }
 */
/**
 * Demonstrates {@link ResponseAccessControl}'s list and single-object overloads on
 * {@link Employee}, a small self-referencing entity ({@code manager} is itself an
 * {@code Employee}), progressing from a top-level field through one and two levels of nested
 * dot-path restriction.
 * <p>
 * The commented Spring controller method above shows the same pattern applied to the
 * {@code Map}-based overload, wrapping a {@code PageDataEntity} result directly.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ResponseAccessExample {

    /**
     * Demonstrates restricting a top-level field ({@code name}) and a one-level-nested field
     * ({@code manager.name}, blanked rather than removed) across a list of sample employees —
     * including one entry whose {@code manager} is {@code null}, to show a restricted path is
     * simply skipped when the object it would reach into doesn't exist.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Employee ceo = new Employee().setId(1).setName("Amirsam").setDepartment("Executive");
        // manager left null on purpose - the CEO has no manager.

        List<Employee> employees = new ArrayList<>();
        employees.add(ceo);
        employees.add(new Employee().setId(2).setName("Mohammad").setDepartment("Engineering").setManager(ceo));
        employees.add(new Employee().setId(3).setName("Farid").setDepartment("Sales").setManager(ceo));

        List<String> limitationList = List.of("name", "manager.name");

        List<Map<String, Object>> result = ResponseAccessControl.apply(employees, limitationList, ResponseAccessControl.EMPTY);
        System.out.println(result);
        for (Map<String, Object> map : result) {
            System.out.println(map);
            System.out.println(map.get("name")); // always blank - "name" is directly restricted
        }
    }

    /**
     * Demonstrates removing an entire nested field ({@code manager}) from a single object.
     *
     * @param args not used
     */
    public static void main2(String[] args) {
        Employee manager = new Employee().setId(1).setName("Amirsam").setDepartment("Executive");
        Employee employee = new Employee().setId(2).setName("Mohammad").setDepartment("Engineering").setManager(manager);

        List<String> limitationList = List.of("manager");

        Map<String, Object> result = ResponseAccessControl.apply(employee, limitationList, ResponseAccessControl.REMOVE);
        System.out.println(result);
    }

    /**
     * Demonstrates a two-level-nested path ({@code manager.manager.name}), across employees at
     * different depths in the reporting chain: the CEO has no manager at all, the VP's manager
     * is the CEO (who has no manager), and the engineer's manager's manager is the CEO. Shows
     * that a multi-level path is blanked only where the full chain actually resolves, and is
     * silently skipped anywhere along the chain that resolves to {@code null}.
     *
     * @param args not used
     */
    public static void main3(String[] args) {
        Employee ceo = new Employee().setId(1).setName("Amirsam").setDepartment("Executive");
        Employee vp = new Employee().setId(2).setName("Mohammad").setDepartment("Engineering").setManager(ceo);
        Employee engineer = new Employee().setId(3).setName("Farid").setDepartment("Engineering").setManager(vp);

        List<Employee> employees = new ArrayList<>();
        employees.add(ceo);
        employees.add(vp);
        employees.add(engineer);

        List<String> limitationList = List.of("manager.manager.name");

        List<Map<String, Object>> result = ResponseAccessControl.apply(employees, limitationList, ResponseAccessControl.EMPTY);
        System.out.println(result);
        for (Map<String, Object> map : result) {
            System.out.println(map);
        }
    }
}