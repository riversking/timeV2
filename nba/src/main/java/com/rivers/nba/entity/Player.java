package com.rivers.nba.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员信息表
 * </p>
 *
 * @author rivers
 * @since 2024-06-16
 */
public class Player implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 球员id
     */
    private Integer playerId;

    /**
     * 球队名称
     */
    private String team;

    /**
     * 球队id
     */
    private Integer teamId;

    /**
     * 球衣号码
     */
    private Integer jersey;

    /**
     * 球员状态
     */
    private String status;

    /**
     * 位置分类
     */
    private String positionCategory;

    /**
     * 球员位置
     */
    private String position;

    /**
     * 球员名字
     */
    private String firstName;

    /**
     * 球员的姓
     */
    private String lastName;

    /**
     * 球员身高 米
     */
    private Integer height;

    /**
     * 球员体重 磅
     */
    private Integer weight;

    /**
     * 球员出生日期
     */
    private LocalDateTime birthDate;

    /**
     * 球员出生城市
     */
    private String birthCity;

    /**
     * 球员出生州
     */
    private String birthState;

    /**
     * 球员出生国家
     */
    private String birthCountry;

    /**
     * 大学
     */
    private String college;

    /**
     * 年薪
     */
    private Integer salary;

    /**
     * 头像
     */
    private String photoUrl;

    /**
     * 职业年限
     */
    private Integer experience;

    /**
     * 球员全称
     */
    private String draftKingsName;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改人
     */
    private String updateUser;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Integer getJersey() {
        return jersey;
    }

    public void setJersey(Integer jersey) {
        this.jersey = jersey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPositionCategory() {
        return positionCategory;
    }

    public void setPositionCategory(String positionCategory) {
        this.positionCategory = positionCategory;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public LocalDateTime getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDateTime birthDate) {
        this.birthDate = birthDate;
    }

    public String getBirthCity() {
        return birthCity;
    }

    public void setBirthCity(String birthCity) {
        this.birthCity = birthCity;
    }

    public String getBirthState() {
        return birthState;
    }

    public void setBirthState(String birthState) {
        this.birthState = birthState;
    }

    public String getBirthCountry() {
        return birthCountry;
    }

    public void setBirthCountry(String birthCountry) {
        this.birthCountry = birthCountry;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public Integer getSalary() {
        return salary;
    }

    public void setSalary(Integer salary) {
        this.salary = salary;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public Integer getExperience() {
        return experience;
    }

    public void setExperience(Integer experience) {
        this.experience = experience;
    }

    public String getDraftKingsName() {
        return draftKingsName;
    }

    public void setDraftKingsName(String draftKingsName) {
        this.draftKingsName = draftKingsName;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "Player{" +
        "id = " + id +
        ", playerId = " + playerId +
        ", team = " + team +
        ", teamId = " + teamId +
        ", jersey = " + jersey +
        ", status = " + status +
        ", positionCategory = " + positionCategory +
        ", position = " + position +
        ", firstName = " + firstName +
        ", lastName = " + lastName +
        ", height = " + height +
        ", weight = " + weight +
        ", birthDate = " + birthDate +
        ", birthCity = " + birthCity +
        ", birthState = " + birthState +
        ", birthCountry = " + birthCountry +
        ", college = " + college +
        ", salary = " + salary +
        ", photoUrl = " + photoUrl +
        ", experience = " + experience +
        ", draftKingsName = " + draftKingsName +
        ", createUser = " + createUser +
        ", createTime = " + createTime +
        ", updateUser = " + updateUser +
        ", updateTime = " + updateTime +
        "}";
    }
}
