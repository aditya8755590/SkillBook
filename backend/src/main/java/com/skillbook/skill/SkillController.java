package com.skillbook.skill;

import com.skillbook.common.ApiResponse;
import com.skillbook.skill.dto.SkillRequest;
import com.skillbook.skill.dto.SkillResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillResponse>>> listSkills(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.list(q)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(
            @Valid @RequestBody SkillRequest request) {
        SkillService.CreationResult result = skillService.getOrCreate(request.name(), request.category());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(SkillResponse.from(result.skill())));
    }
}