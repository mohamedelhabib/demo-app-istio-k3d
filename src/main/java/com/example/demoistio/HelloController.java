package com.example.demoistio;

import com.github.javafaker.Faker;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class HelloController {

  private static final String template = "Hello, %s!";
  private static final Faker faker = new Faker();

  
  @RequestMapping("/hello")
  public String hello() {
    return String.format(template, faker.name().fullName());
  }
}