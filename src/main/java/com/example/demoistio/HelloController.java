package com.example.demoistio;

import com.github.javafaker.Faker;
import com.github.javafaker.Name;
import java.util.Date;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2")
public class HelloController {

  private static final Faker faker = new Faker();

  @RequestMapping("/hello")
  public Person hello() {
    return new Person(faker.name());
  }

  @Data
  class Person {

    private String firstName;
    private String middleName;
    private String lastName;
    private Date birthDay;

    public Person(Name name) {
      this.firstName = name.firstName();
      this.middleName = name.nameWithMiddle();
      this.lastName = name.lastName();
      this.birthDay = faker.date().birthday();
    }
  }
}
