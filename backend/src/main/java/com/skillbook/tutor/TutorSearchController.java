package com.skillbook.tutor;

import com.skillbook.common.ApiResponse;
import com.skillbook.tutor.dto.TutorSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
public class TutorSearchController {

    private final TutorSearchService tutorSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TutorSearchResponse>>> searchTutors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) BigDecimal minRate,
            @RequestParam(required = false) BigDecimal maxRate,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "rating_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<TutorSearchResponse> result = tutorSearchService.search(
                q, skill, minRate, maxRate, minRating, sort, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}