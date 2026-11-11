package com.evanlink.model;

import jakarta.persistence.*;

@Entity
@Table(name = "skills")
public class Skill {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @Column(name = "name_en")
    private String nameEn;
    
    private Integer level;
    
    @Column(name = "classify")
    private String classify;
    
    @Column(name = "classify_en")
    private String classifyEn;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    
    public String getClassify() { return classify; }
    public void setClassify(String classify) { this.classify = classify; }
    
    public String getClassifyEn() { return classifyEn; }
    public void setClassifyEn(String classifyEn) { this.classifyEn = classifyEn; }
}
