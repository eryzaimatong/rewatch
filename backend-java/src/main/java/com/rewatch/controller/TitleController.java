package com.rewatch.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/titles")
public class TitleController {

    @Autowired
    private TitleRepository titleRepository;

    @GetMapping
    public List<Title> getAllTitles() {
        return titleRepository.findAll();
    }
}