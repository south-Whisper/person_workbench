package com.example.demo.controller.publicworkflow;

import com.example.demo.service.talent.TalentService;
import com.example.demo.service.offer.OfferResponseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PublicWorkflowController {
    private final TalentService talents;
    private final OfferResponseService offerResponses;

    public PublicWorkflowController(TalentService talents, OfferResponseService offerResponses) {
        this.talents = talents;
        this.offerResponses = offerResponses;
    }

    @PostMapping("/public/questionnaire")
    public Map<String, Object> questionnaire(@RequestBody Map<String, Object> input) {
        return talents.submitQuestionnaire(input);
    }

    @PostMapping("/questionnaire-invitations")
    public Map<String, Object> sendQuestionnaire(@RequestBody Map<String, Object> input) {
        return talents.sendQuestionnaire(input);
    }

    @GetMapping("/public/questionnaires/{token}")
    public Map<String, Object> questionnaireInvitation(@PathVariable String token) {
        return talents.publicQuestionnaire(token);
    }

    @PostMapping("/public/questionnaires/{token}")
    public Map<String, Object> submitQuestionnaireInvitation(
        @PathVariable String token,
        @RequestBody Map<String, Object> input
    ) {
        return talents.submitQuestionnaire(token, input);
    }

    @GetMapping("/public/positions")
    public List<Map<String, Object>> publicPositions() {
        return talents.publicPositions();
    }

    @GetMapping("/public/offers/{token}")
    public Map<String, Object> publicOffer(@PathVariable String token) {
        return offerResponses.find(token);
    }

    @PostMapping("/public/offers/{token}/response")
    public Map<String, Object> respondToOffer(
        @PathVariable String token,
        @RequestBody Map<String, Object> input
    ) {
        return offerResponses.respond(token, input);
    }

    @GetMapping(value = "/public/offers/{token}/accept", produces = MediaType.TEXT_HTML_VALUE)
    public String acceptOffer(@PathVariable String token) {
        return offerResponses.acceptAndRender(token);
    }
}


