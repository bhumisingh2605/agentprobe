package com.agentprobe.judge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/judge")
public class JudgeController {

    public record PreviewRequest(
            @NotBlank @Size(max = 2000) String rubric,
            @NotBlank @Size(max = 8000) String input,
            @NotBlank @Size(max = 8000) String answer,
            @Size(max = 4000) String reference,
            @Min(1) @Max(5) Integer votes) {
    }

    private final JudgeService judge;

    public JudgeController(JudgeService judge) {
        this.judge = judge;
    }

    /** Try a rubric on a hand-written answer. rubric can be a library name or custom text. */
    @PostMapping("/preview")
    public JudgeService.Verdict preview(@Valid @RequestBody PreviewRequest req) {
        return judge.judge(Rubrics.resolve(req.rubric()), req.input(), req.answer(), "[]",
                req.reference(), req.votes() == null ? 1 : req.votes());
    }

    @org.springframework.web.bind.annotation.GetMapping("/rubrics")
    public Set<String> rubrics() {
        return Rubrics.names();
    }
}