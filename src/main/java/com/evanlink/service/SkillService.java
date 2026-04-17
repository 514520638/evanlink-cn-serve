package com.evanlink.service;

import com.evanlink.model.Skill;
import com.evanlink.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    @Autowired
    private SkillRepository skillRepository;

    /**
     * 获取所有技能，按分类分组
     */
    public List<Map<String, Object>> getAllSkillsGrouped() {
        List<Skill> skills = skillRepository.findAll();
        
        // 按 classify 分组
        Map<String, List<Skill>> groupedByClassify = skills.stream()
            .collect(Collectors.groupingBy(s -> s.getClassify() != null ? s.getClassify() : "other"));
        
        // 转换为需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map.Entry<String, List<Skill>> entry : groupedByClassify.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            List<Skill> groupSkills = entry.getValue();
            
            if (!groupSkills.isEmpty()) {
                group.put("classify", entry.getKey());
                group.put("classifyEn", groupSkills.get(0).getClassifyEn());
                
                List<Map<String, Object>> skillList = groupSkills.stream()
                    .map(skill -> {
                        Map<String, Object> skillMap = new HashMap<>();
                        skillMap.put("name", skill.getName());
                        skillMap.put("nameEn", skill.getNameEn());
                        skillMap.put("level", skill.getLevel());
                        return skillMap;
                    })
                    .collect(Collectors.toList());
                
                group.put("list", skillList);
                result.add(group);
            }
        }
        
        return result;
    }

    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    public Skill save(Skill skill) {
        return skillRepository.save(skill);
    }

    public void deleteById(Long id) {
        skillRepository.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteAllAndReinitialize() {
        skillRepository.deleteAll();
        
        // 前端
        saveSkill("Vue", "Vue", 90, "前端", "frontend");
        saveSkill("小程序", "Mini-program", 85, "前端", "frontend");
        saveSkill("React", "React", 70, "前端", "frontend");
        
        // AI
        saveSkill("Java", "Java", 60, "AI", "AI");
        saveSkill("大模型应用", "Large Model Applications", 85, "AI", "AI");
        saveSkill("OpenClaw", "OpenClaw", 75, "AI", "AI");
        
        // DevOps
        saveSkill("Xone", "Xone", 90, "DevOps", "devops");
        saveSkill("腾讯云", "Tencent Cloud", 85, "DevOps", "devops");
        saveSkill("CI/CD", "CI/CD", 80, "DevOps", "devops");
        
        // tools
        saveSkill("Git", "Git", 90, "tools", "tools");
        saveSkill("GitLab插件", "GitLab Plugin", 90, "tools", "tools");
        saveSkill("Tapd", "Tapd", 95, "tools", "tools");
    }
    
    private void saveSkill(String name, String nameEn, Integer level, String classify, String classifyEn) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setNameEn(nameEn);
        skill.setLevel(level);
        skill.setClassify(classify);
        skill.setClassifyEn(classifyEn);
        skillRepository.save(skill);
    }
}
