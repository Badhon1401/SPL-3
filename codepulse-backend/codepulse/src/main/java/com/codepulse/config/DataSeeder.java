package com.codepulse.config;

import com.codepulse.entity.Topic;
import com.codepulse.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final TopicRepository topicRepository;

    private static final List<String[]> TOPICS = List.of(
            new String[]{"Dynamic Programming", "dp"},
            new String[]{"Graph", "graph"},
            new String[]{"Binary Search", "binary-search"},
            new String[]{"Greedy", "greedy"},
            new String[]{"Data Structures", "data-structures"},
            new String[]{"Math", "math"},
            new String[]{"String", "string"},
            new String[]{"Sorting", "sorting"},
            new String[]{"Two Pointers", "two-pointers"},
            new String[]{"Recursion", "recursion"},
            new String[]{"Backtracking", "backtracking"},
            new String[]{"Bit Manipulation", "bit-manipulation"},
            new String[]{"Trees", "trees"},
            new String[]{"Number Theory", "number-theory"},
            new String[]{"Geometry", "geometry"},
            new String[]{"Combinatorics", "combinatorics"},
            new String[]{"Divide and Conquer", "divide-and-conquer"},
            new String[]{"Hashing", "hashing"},
            new String[]{"Stack", "stack"},
            new String[]{"Queue", "queue"}
    );

    @Bean
    public ApplicationRunner seedTopics() {
        return args -> {
            int seeded = 0;
            for (String[] t : TOPICS) {
                String slug = t[1];
                if (topicRepository.findBySlug(slug).isEmpty()) {
                    topicRepository.save(Topic.builder().name(t[0]).slug(slug).build());
                    seeded++;
                }
            }
            if (seeded > 0) log.info("Seeded {} topics into database.", seeded);
        };
    }
}
