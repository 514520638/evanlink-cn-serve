package com.evanlink.controller;

import com.evanlink.model.Skill;
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

    /**
     * 保存单个技能
     */
    @PostMapping
    public ResponseEntity<Skill> saveSkill(@RequestBody Skill skill) {
        Skill saved = skillService.save(skill);
        return ResponseEntity.ok(saved);
    }

    /**
     * 删除技能
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long id) {
        skillService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 删除所有技能并重新初始化默认数据
     */
    @PostMapping("/reinitialize")
    public ResponseEntity<String> reinitializeSkills() {
        skillService.deleteAllAndReinitialize();
        return ResponseEntity.ok("Skills reinitialized successfully");
    }
}
