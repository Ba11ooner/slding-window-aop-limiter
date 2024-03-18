package com.example.controller;

import com.example.annotation.RequestLimit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class HelloController {

    @RequestLimit()
    @GetMapping("/hello")
    public ResponseEntity<String> hello(HttpServletRequest request){
        ResponseEntity<String> response = new ResponseEntity("hello",HttpStatus.OK);
        return response;
    }
}
