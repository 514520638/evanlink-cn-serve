package com.evanlink.config;

import com.evanlink.model.UserInfo;
import com.evanlink.model.Skill;
import com.evanlink.repository.UserInfoRepository;
import com.evanlink.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Override
    public void run(String... args) {
        // 如果数据库已有数据，则不初始化
        if (userInfoRepository.count() > 0) {
            return;
        }

        // 创建 UserInfo
        UserInfo userInfo = new UserInfo();
        userInfo.setName("Evan");
        userInfo.setNameEn("Evan");
        userInfo.setTitle("全栈工程师");
        userInfo.setTitleEn("Full-Stack Engineer");
        userInfo.setBio("我是一名热爱技术的全栈工程师，目前专注于 Web 开发、云原生和性能优化领域。工作之余，我喜欢阅读技术文档、参与开源项目，也会写一些技术博客来记录和分享所学。希望能通过这个博客与更多志同道合的朋友交流学习。");
        userInfo.setBioEn("I am a full-stack engineer passionate about technology, currently focusing on web development, cloud-native, and performance optimization. In my spare time, I enjoy reading technical documentation, contributing to open-source projects, and writing technical blogs to share my knowledge. I hope to connect and learn with like-minded friends through this blog.");
        userInfo.setAvatar("https://i.ibb.co/gLbjvJ4T/2026-04-09-143013-451.jpg");
        userInfo.setWechat("https://i.ibb.co/rfL9RGng/vchat-JPG.jpg");
        userInfo.setPhoneNumber("14776866846");
        userInfo.setEmail("514520638@qq.com");
        userInfo.setGithub("https://github.com/514520638");
        userInfo.setGitee("https://gitee.com/gitee_evan/projects");
        userInfo.setVisitorNumber(0L);

        userInfoRepository.save(userInfo);

        // 创建 Skills（按新结构）
        // 前端
        createSkill("Vue", "Vue", 90, "前端", "frontend");
        createSkill("小程序", "Mini-program", 85, "前端", "frontend");
        createSkill("React", "React", 70, "前端", "frontend");
        
        // AI
        createSkill("Java", "Java", 60, "AI", "AI");
        createSkill("大模型应用", "Large Model Applications", 85, "AI", "AI");
        createSkill("OpenClaw", "OpenClaw", 75, "AI", "AI");
        
        // DevOps
        createSkill("Xone", "Xone", 90, "DevOps", "devops");
        createSkill("腾讯云", "Tencent Cloud", 85, "DevOps", "devops");
        createSkill("CI/CD", "CI/CD", 80, "DevOps", "devops");
        
        // tools
        createSkill("Git", "Git", 90, "tools", "tools");
        createSkill("GitLab插件", "GitLab Plugin", 90, "tools", "tools");
        createSkill("Tapd", "Tapd", 95, "tools", "tools");
        
        System.out.println("===== UserInfo 和 Skills 数据初始化完成 =====");
    }
    
    private void createSkill(String name, String nameEn, Integer level, String classify, String classifyEn) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setNameEn(nameEn);
        skill.setLevel(level);
        skill.setClassify(classify);
        skill.setClassifyEn(classifyEn);
        skillRepository.save(skill);
    }
}
