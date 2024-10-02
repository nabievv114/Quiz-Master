package com.example.quizmaster.service;

import com.example.quizmaster.entity.Answer;
import com.example.quizmaster.entity.Question;
import com.example.quizmaster.exception.ResourceNotFoundException;
import com.example.quizmaster.payload.ApiResponse;
import com.example.quizmaster.payload.CustomPageable;
import com.example.quizmaster.payload.request.RequestAnswer;
import com.example.quizmaster.payload.request.RequestQuestion;
import com.example.quizmaster.payload.response.ResponseAnswer;
import com.example.quizmaster.payload.response.ResponseQuestion;
import com.example.quizmaster.repository.AnswerRepository;
import com.example.quizmaster.repository.QuestionRepository;
import com.example.quizmaster.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final AnswerRepository answerRepository;

    // savollarni saqlash
    public ApiResponse saveQuestion(RequestQuestion requestQuestion) {
        Question question = Question.builder()
                .question_text(requestQuestion.getText())
                .quiz(quizRepository.findById(requestQuestion.getQuizId())
                        .orElseThrow(() -> new ResourceNotFoundException("Quiz not found")))
                .build();

        Question save = questionRepository.save(question);

        List<Answer> answers = new ArrayList<>();
        for (RequestAnswer answer : requestQuestion.getAnswerList()) {
            Answer answer1 = Answer.builder()
                    .answerText(answer.getText())
                    .isCorrect(answer.isCorrect())
                    .question(save)
                    .build();
            answers.add(answer1);

            answerRepository.save(answer1);
        }
        question.setAnswers(answers);

        return new ApiResponse("Question saved successfully", HttpStatus.CREATED);
    }

    // barcha savolni olish
    public ApiResponse getAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Question> questionPage = questionRepository.findAll(pageRequest);
        List<ResponseQuestion> responseQuestions = toResponseQuestion(questionPage.getContent());

        CustomPageable pageableResponse = CustomPageable.builder()
                .size(size)
                .page(page)
                .totalPage(questionPage.getTotalPages())
                .totalElements(questionPage.getTotalElements())
                .data(responseQuestions)
                .build();

        return new ApiResponse("Questions retrieved successfully", HttpStatus.OK, pageableResponse);

    }

    // savolni yangilash
    public ApiResponse updateQuestion(Long id, RequestQuestion requestQuestion) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        question.setQuestion_text(requestQuestion.getText());
        question.setQuiz(quizRepository.findById(requestQuestion.getQuizId())
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found")));

        List<Answer> updatedAnswers = new ArrayList<>();
        for (RequestAnswer requestAnswer : requestQuestion.getAnswerList()) {
            Answer answer;

            if (requestAnswer.getId() != null) {
                answer = answerRepository.findById(requestAnswer.getId())
                        .orElse(new Answer());
            } else {
                answer = new Answer();
            }

            answer.setAnswerText(requestAnswer.getText());
            answer.setIsCorrect(requestAnswer.isCorrect());
            answer.setQuestion(question);

            updatedAnswers.add(answerRepository.save(answer));
        }

        List<Answer> existingAnswers = question.getAnswers();
        existingAnswers.stream()
                .filter(existingAnswer -> updatedAnswers.stream()
                        .noneMatch(updatedAnswer -> updatedAnswer.getId().equals(existingAnswer.getId())))
                .forEach(answerRepository::delete);

        question.setAnswers(updatedAnswers);

        questionRepository.save(question);

        return new ApiResponse("Question updated successfully", HttpStatus.OK);
    }

    // savolni o'chirish
    public ApiResponse deleteQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        answerRepository.deleteAll(question.getAnswers());

        questionRepository.delete(question);

        return new ApiResponse("Question deleted successfully", HttpStatus.OK);
    }


    // savolni idi bo'yicha olish
    public ApiResponse getOne(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        ResponseQuestion responseQuestion = (ResponseQuestion) toResponseQuestion((List<Question>) question);

        return new ApiResponse("Question retrieved successfully", HttpStatus.OK, responseQuestion);
    }

    // question obektini response qilish
    public List<ResponseQuestion> toResponseQuestion(List<Question> questions) {
        List<ResponseQuestion> responseQuestions = new ArrayList<>();

        // har bir question obyektlarni korish uchun
        for (Question question : questions) {
            List<ResponseAnswer> responseAnswers = new ArrayList<>();
            // question boyicha answer ni olish
            for (Answer answer : question.getAnswers()) {
                // answer obyektini response qilish
                ResponseAnswer responseAnswer = ResponseAnswer.builder()
                        .id(answer.getId())
                        .text(answer.getAnswerText())
                        .isCorrect(answer.getIsCorrect())
                        .build();

                responseAnswers.add(responseAnswer);
            }
            // question obyektni response qilish
            ResponseQuestion responseQuestion = ResponseQuestion.builder()
                    .id(question.getId())
                    .text(question.getQuestion_text())
                    .answers(responseAnswers)
                    .quizId(question.getQuiz().getId())
                    .build();
            // responseQuestion obyektini ruyxatga qoshish
            responseQuestions.add(responseQuestion);
        }

        return responseQuestions;
    }
}
