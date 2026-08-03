package org.j2os.examples.web.api;

import lombok.RequiredArgsConstructor;
import org.j2os.examples.web.entity.person.Car;
import org.j2os.examples.web.entity.person.Factory;
import org.j2os.examples.web.entity.person.Location;
import org.j2os.examples.web.entity.person.Person;
import org.j2os.platform.page2.PageDataList;
import org.j2os.platform.page2.PageDataResultFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates {@link PageDataResultFilter} over {@link PageDataList}: {@link #getPerson}
 * returns a filtered/searchable/sortable list of {@link Person} records from an in-memory list,
 * and {@link #getPersonFilter} post-processes that same result field by field - removing,
 * blanking, replacing, and adding both flat and nested fields.
 */
@RestController
@RequiredArgsConstructor
public class PageListExample {

    /**
     * Returns {@link #getPerson}'s result with several post-processing rules applied: a field
     * removed, a field blanked, existing fields replaced, and new (including nested and
     * list-valued) fields added.
     *
     * @param params the page2 request parameters (paging/search/sort), forwarded to {@link #getPerson}
     * @return the post-processed page2 result
     */
    @GetMapping("/personFilter")
    @SuppressWarnings("unchecked")
    public Object getPersonFilter(@RequestParam Map<String, Object> params) {
        PageDataResultFilter<Person> filter = new PageDataResultFilter<>(getPerson(params));

        return filter
                // Plain removal.
                .remove("firstName")

                // Other ways to remove a (sub-)field, for reference:
                // .remove("car.name")   - remove a field one level into a nested object
                // .remove("car.factory") - remove a field two levels in
                // .remove("car")         - remove the entire nested object
                // .mask("car.name")      - mask instead of remove (e.g. for sensitive data like a password)

                // Blank out a field.
                .empty("lastName")

                // Replace an existing field's value (output can be of any type).
                .put("firstName", person -> "Mr." + person.getFirstName())

                // Add a new field. Note: this field only exists in the JSON output, not on the
                // underlying Person object - here firstName still refers to the original value
                // (without "Mr."), since this lambda runs against the original entity.
                .put("fullName", person -> person.getFirstName() + " " + person.getLastName())

                // Add a new list-valued field (can be any type).
                .put("languages", person -> Arrays.asList("JAPAN", "ENGLISH"))

                // Add a new field nested two levels in (car.factory).
                .put("car.factory", person -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", person.getCar().getFactory().getName());
                    map.put("computerLanguage", Arrays.asList("Java", "Rust", "C"));
                    return map; // can be any type
                })

                // Alternative: mutate an existing nested field in place instead of replacing it
                // with a plain Map. This overload isn't used above (car.factory is already
                // handled by the .put(String, Function) call above it), but is kept here as a
                // reference for when you want typed access to the current Factory object itself:
                // .put("car.factory", Factory.class, (person, factory) -> {
                //     factory.setName("NEW FACTORY NAME: " + factory.getName());
                //     return factory; // input and output are typed as Factory, because of Factory.class
                // })

                // Add an element to a list field that was itself created virtually above
                // (the "languages" field added by the .put(String, Function) call earlier).
                .put("languages", List.class, (person, languages) -> {
                    languages.add("PERSIAN");
                    return languages; // input and output are typed as List, because of List.class
                })

                // Mutate every element of an existing list-of-objects field (locations).
                .put("locations", List.class, (person, locations) -> {
                    for (Object location : locations) {
                        var locationMap = (Map<String, Object>) location;
                        locationMap.put("geoAddress", "IRAN." + locationMap.get("geoAddress"));
                        locationMap.put("lat&long", person.getId() + person.getId());
                    }
                    return locations; // input and output are typed as List, because of List.class
                })
                .getResult();
    }

    /**
     * Returns a paginated/searchable/sortable list of sample {@link Person} records from an
     * in-memory list via {@link PageDataList}, filtered to car factories/models matching any of
     * several {@code LIKE} conditions.
     *
     * @param params the page2 request parameters (paging/search/sort)
     * @return the page2 result
     */
    @GetMapping("/person")
    public Map<String, Object> getPerson(@RequestParam Map<String, Object> params) {
        List<Person> persons = new ArrayList<>();
        persons.add(new Person().setId(1).setFirstName("Mohammad").setLastName("Ghaderi").setCar(new Car().setFactory(new Factory().setName("BMW"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(3).setFirstName("Amirsam").setLastName("Bahador").setCar(new Car().setName("CERATO").setFactory(new Factory().setName("KIA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(4).setFirstName("Farid").setLastName("Ghaderi").setCar(new Car().setName("JETA").setFactory(new Factory().setName("FW"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(5).setFirstName("ALI").setLastName("Ghaderi").setCar(new Car().setName("206").setFactory(new Factory().setName("IK"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(6).setFirstName("Erfan").setLastName("Entezari").setCar(new Car().setName("CERATO").setFactory(new Factory().setName("KIA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(7).setFirstName("Masoud").setLastName("Nori").setCar(new Car().setName("CERATO").setFactory(new Factory().setName("KIA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(8).setFirstName("Amir").setLastName("Bahador").setCar(new Car().setName("S500").setFactory(new Factory().setName("BENZ"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(9).setFirstName("Farhad").setLastName("Ramezani").setCar(new Car().setName("ORION").setFactory(new Factory().setName("TOYOTA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(10).setFirstName("Hasan").setLastName("Salami").setCar(new Car().setName("S500").setFactory(new Factory().setName("BENZ"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(11).setFirstName("Fazel").setLastName("Moayeri").setCar(new Car().setName("Corola").setFactory(new Factory().setName("TOYOTA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(12).setFirstName("Jamal").setLastName("Jamali").setCar(new Car().setName("JamalMashin").setFactory(new Factory().setName("JamalKarkhane"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(13).setFirstName("Shahab").setLastName("Darvish").setCar(new Car().setName("RIO").setFactory(new Factory().setName("KIA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(14).setFirstName("Majid").setLastName("Darvish").setCar(new Car().setName("COROLA").setFactory(new Factory().setName("TOYOTA"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(15).setFirstName("Reza").setLastName("Rezaei").setCar(new Car().setName("RezaMashin").setFactory(new Factory().setName("RezaKarkhane"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));
        persons.add(new Person().setId(16).setFirstName("Daryoush").setLastName("Abdollahi").setCar(new Car().setName("207").setFactory(new Factory().setName("IK"))).setLocations(Arrays.asList(new Location().setGeoAddress("Tehran"), new Location().setGeoAddress("KISH"))));

        PageDataList dynamicData = new PageDataList();

        return dynamicData
                .searchAndSortOn("id", "firstName", "lastName", "car.factory.name", "car.name")
                .where("car.factory.name", "LIKE", "toyota")
                .or("car.factory.name", "LIKE", "KIA")
                .or("car.name", "LIKE", "JETA")
                .or("car.name", "LIKE", "X4")
                .or("car.factory.name", "LIKE", "BMW")
                .or("car.factory.name", "LIKE", "IK")
                .getResult(persons, params);
    }
}