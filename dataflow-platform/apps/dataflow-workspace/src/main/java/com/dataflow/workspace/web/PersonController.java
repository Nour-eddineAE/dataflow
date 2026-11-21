package com.dataflow.workspace.web;

import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.folders.Person;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PersonController {

    private final People people;

    public PersonController(People people) {
        this.people = people;
    }

    @GetMapping("/me")
    public Me me() {
        Person person = people.current();
        return new Me(person.id(), person.name(), person.email(), people.groups(), people.admin());
    }

    public record Me(String id, String name, String email, List<String> groups, boolean admin) {}
}
