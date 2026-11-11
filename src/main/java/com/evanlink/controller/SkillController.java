package com.evanlink.controller;

import com.evanlink.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "*")
public class SkillController {

    @Autowired
    private SkillService skillService;

    /**
     * 获取分组后的技能列表
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllSkills() {
        List<Map<String, Object>> skills = skillService.getAllSkillsGrouped();
        return ResponseEntity.ok(skills);
    }
}
