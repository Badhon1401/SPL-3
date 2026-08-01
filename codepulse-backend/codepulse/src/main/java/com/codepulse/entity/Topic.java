package com.codepulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Dynamic Programming", "Graph", "Binary Search"

    @Column
    private String slug; // e.g., "dp", "graph", "binary-search"

    @ManyToMany(mappedBy = "topics")
    @Builder.Default
    private List<Problem> problems = new ArrayList<>();
}
