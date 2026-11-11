package com.evanlink.service;

import com.evanlink.model.Skill;
import com.evanlink.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public List<Skill> replaceAll(List<Skill> skills) {
        skillRepository.deleteAll();
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }

        skills.forEach(skill -> skill.setId(null));
        return skillRepository.saveAll(skills);
    }
}
