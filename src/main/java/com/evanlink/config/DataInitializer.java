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

        // 创建 Skills（独立于 UserInfo）
        Skill skill1 = new Skill();
        skill1.setName("React");
        skill1.setNameEn("React");
        skill1.setLevel(90);
        skill1.setCategory("frontend");

        Skill skill2 = new Skill();
        skill2.setName("TypeScript");
        skill2.setNameEn("TypeScript");
        skill2.setLevel(85);
        skill2.setCategory("frontend");

        Skill skill3 = new Skill();
        skill3.setName("Vue.js");
        skill3.setNameEn("Vue.js");
        skill3.setLevel(80);
        skill3.setCategory("frontend");

        Skill skill4 = new Skill();
        skill4.setName("Go");
        skill4.setNameEn("Go");
        skill4.setLevel(75);
        skill4.setCategory("backend");

        Skill skill5 = new Skill();
        skill5.setName("Node.js");
        skill5.setNameEn("Node.js");
        skill5.setLevel(80);
        skill5.setCategory("backend");

        Skill skill6 = new Skill();
        skill6.setName("Python");
        skill6.setNameEn("Python");
        skill6.setLevel(70);
        skill6.setCategory("backend");

        Skill skill7 = new Skill();
        skill7.setName("Docker");
        skill7.setNameEn("Docker");
        skill7.setLevel(85);
        skill7.setCategory("devops");

        Skill skill8 = new Skill();
        skill8.setName("Kubernetes");
        skill8.setNameEn("Kubernetes");
        skill8.setLevel(70);
        skill8.setCategory("devops");

        Skill skill9 = new Skill();
        skill9.setName("CI/CD");
        skill9.setNameEn("CI/CD");
        skill9.setLevel(80);
        skill9.setCategory("devops");

        Skill skill10 = new Skill();
        skill10.setName("Git");
        skill10.setNameEn("Git");
        skill10.setLevel(90);
        skill10.setCategory("tools");

        Skill skill11 = new Skill();
        skill11.setName("Linux");
        skill11.setNameEn("Linux");
        skill11.setLevel(85);
        skill11.setCategory("tools");

        Skill skill12 = new Skill();
        skill12.setName("PostgreSQL");
        skill12.setNameEn("PostgreSQL");
        skill12.setLevel(75);
        skill12.setCategory("tools");

        skillRepository.saveAll(Arrays.asList(skill1, skill2, skill3, skill4, skill5, skill6, skill7, skill8, skill9, skill10, skill11, skill12));
        
        System.out.println("===== UserInfo 和 Skills 数据初始化完成 =====");
    }
}
