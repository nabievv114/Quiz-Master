package com.example.quizmaster.service;

import com.example.quizmaster.entity.*;
import com.example.quizmaster.payload.ApiResponse;
import com.example.quizmaster.payload.CustomPageable;
import com.example.quizmaster.payload.request.ReqPassTest;
import com.example.quizmaster.payload.request.RequestQuiz;
import com.example.quizmaster.payload.response.ResponseQuestion;
import com.example.quizmaster.payload.response.ResponseQuiz;
import com.example.quizmaster.repository.AnswerRepository;
import com.example.quizmaster.repository.QuestionRepository;
import com.example.quizmaster.repository.QuizRepository;
import com.example.quizmaster.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionService questionService;
    private final ResultRepository resultRepository;
    private final AnswerRepository answerRepository;

    // Yangi testni saqlash
    public ApiResponse save(RequestQuiz requestQuiz) {
        Quiz quiz = Quiz.builder()
                .title(requestQuiz.getTitle())
                .description(requestQuiz.getDescription())
                .createdAt(LocalDate.now())
                .questionCount(requestQuiz.getQuestionCount())
                .timeLimit(requestQuiz.getTimeLimit())
                .build();

        quizRepository.save(quiz);
        return new ApiResponse("Test muvaffaqiyatli saqlandi!", HttpStatus.CREATED);
    }

    // Barcha testlarni olish
    public ApiResponse getAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Quiz> quizPage = quizRepository.findAll(pageRequest);

        List<ResponseQuiz> responseQuizList = quizPage.getContent().stream()
                .map(this::mapToResponseQuiz)
                .collect(Collectors.toList());

        CustomPageable pageable = CustomPageable.builder()
                .page(page)
                .size(size)
                .totalPage(quizPage.getTotalPages())
                .totalElements(quizPage.getTotalElements())
                .data(responseQuizList)
                .build();

        return new ApiResponse("Testlar muvaffaqiyatli olindi!", HttpStatus.OK, pageable);
    }

    // Testni yangilash
    public ApiResponse update(Long id, RequestQuiz requestQuiz) {
        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) {
            return new ApiResponse("Test topilmadi!", HttpStatus.NOT_FOUND);
        }

        quiz.setTitle(requestQuiz.getTitle());
        quiz.setDescription(requestQuiz.getDescription());
        quiz.setQuestionCount(requestQuiz.getQuestionCount());
        quiz.setTimeLimit(requestQuiz.getTimeLimit());

        quizRepository.save(quiz);
        return new ApiResponse("Test muvaffaqiyatli yangilandi!", HttpStatus.OK);
    }

    // Testni o'chirish
    public ApiResponse delete(Long id) {
        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) {
            return new ApiResponse("Test topilmadi!", HttpStatus.NOT_FOUND);
        }

        quizRepository.delete(quiz);
        return new ApiResponse("Test muvaffaqiyatli o'chirildi!", HttpStatus.OK);
    }

    // Test uchun tasodifiy savollarni olish
    public ApiResponse getRandomQuestionsForQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null) {
            return new ApiResponse("Test topilmadi!", HttpStatus.NOT_FOUND);
        }

        List<Question> allQuestions = questionRepository.findAllByQuizId(quizId);
        List<ResponseQuestion> responseQuestions = questionService.toResponseQuestion(allQuestions);
        Collections.shuffle(responseQuestions);

        int questionCount = quiz.getQuestionCount();

        if (responseQuestions.size() > questionCount) {
            return new ApiResponse("Muvaffaqiyat!", HttpStatus.OK, responseQuestions.subList(0, questionCount));
        }

        return new ApiResponse("Yetarli savollar mavjud emas!", HttpStatus.CONFLICT, responseQuestions);
    }

    // Testni o'tkazish
    public ApiResponse passTest(List<ReqPassTest> passTestList, User user, Long quizId, Long timeTaken) {
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null) {
            return new ApiResponse("Test topilmadi!", HttpStatus.NOT_FOUND);
        }

        List<Long> questionIds = passTestList.stream()
                .map(ReqPassTest::getQuestionId)
                .collect(Collectors.toList());

        List<Answer> correctAnswers = answerRepository.findCorrectAnswersByQuestionIds(questionIds);

        long correctCountAnswers = passTestList.stream()
                .filter(reqPassTest -> correctAnswers.stream()
                        .anyMatch(answer -> answer.getId().equals(reqPassTest.getAnswerId())))
                .count();

        Result result = Result.builder()
                .correctAnswers((int) correctCountAnswers)
                .quiz(quiz)
                .totalQuestion(passTestList.size())
                .user(user)
                .timeTaken(timeTaken)
                .build();

        resultRepository.save(result);
        return new ApiResponse("Test muvaffaqiyatli o'tkazildi!", HttpStatus.OK);
    }

    // Bitta testni olish
    public ApiResponse getOne(Long id) {
        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) {
            return new ApiResponse("Test topilmadi!", HttpStatus.NOT_FOUND);
        }

        ResponseQuiz responseQuiz = ResponseQuiz.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .createdAt(quiz.getCreatedAt())
                .timeLimit(quiz.getTimeLimit())
                .questionCount(quiz.getQuestionCount())
                .build();

        return new ApiResponse("Test topildi!", HttpStatus.OK, responseQuiz);
    }

    // Bir nechta testlar uchun tasodifiy savollarni olish
    public ApiResponse getRandomQuestionsForMultipleQuizzes(List<Long> quizIds) {
        List<Quiz> quizzes = quizRepository.findAllById(quizIds);
        if (quizzes.isEmpty()) {
            return new ApiResponse("Bir yoki bir necha test topilmadi!", HttpStatus.NOT_FOUND);
        }

        List<Question> allQuestions = quizzes.stream()
                .flatMap(quiz -> questionRepository
                .findAllByQuizId(quiz.getId()).stream()).toList();

        List<ResponseQuestion> responseQuestions = questionService.toResponseQuestion(allQuestions);
        Collections.shuffle(responseQuestions);

        int totalQuestionCount = quizzes.stream()
                .mapToInt(Quiz::getQuestionCount)
                .sum();

        if (responseQuestions.size() >= totalQuestionCount) {
            return new ApiResponse("Muvaffaqiyat!", HttpStatus.OK, responseQuestions.subList(0, totalQuestionCount));
        }

        return new ApiResponse("Yetarli savollar mavjud emas!", HttpStatus.CONFLICT, responseQuestions);
    }

    // Testlarni javob formatiga o'zgartirish
    private ResponseQuiz mapToResponseQuiz(Quiz quiz) {
        return ResponseQuiz.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .createdAt(quiz.getCreatedAt())
                .timeLimit(quiz.getTimeLimit())
                .questionCount(quiz.getQuestionCount())
                .build();
    }
}
