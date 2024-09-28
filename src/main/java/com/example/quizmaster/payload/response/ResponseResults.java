package com.example.quizmaster.payload.response;

import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class ResponseResults {
    private Long id;
    private Long user;
    private int totalQuestion;
    private int correctAnswers;
    private LocalDateTime timeTaken;
    private Long quiz;
}