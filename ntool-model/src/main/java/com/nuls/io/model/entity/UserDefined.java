package com.nuls.io.model.entity;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.nuls.io.model.common.CodeEnum;
import com.nuls.io.model.exception.ProcessException;

/**
 * 用户自定义属性表
 * Created by ambition on 2017/7/31.
 */
@Entity
@Table(name = "user_defined", schema = "ambitionj2c", catalog = "")
public class UserDefined {
    private Long   recId;
    private Long   uId;
    private Long   propertyId;
    private String propertyValue;

    /*rec_id：主键id
    u_id：用户id
    property_id：属性id
    property_value：属性值*/

    @Id
    @Column(name = "rec_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getRecId() {
        return recId;
    }

    public void setRecId(Long recId) {
        this.recId = recId;
    }

    @Basic
    @Column(name = "u_id", nullable = false)
    public Long getuId() {
        return uId;
    }

    public void setuId(Long uId) {
        this.uId = uId;
    }

    @Basic
    @Column(name = "property_id", nullable = false)
    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    @Basic
    @Column(name = "property_value", nullable = true, length = 255)
    public String getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        UserDefined that = (UserDefined) o;

        if (recId != that.recId)
            return false;
        if (uId != that.uId)
            return false;
        if (propertyId != that.propertyId)
            return false;
        if (propertyValue != null ? !propertyValue.equals(that.propertyValue)
            : that.propertyValue != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (recId ^ (recId >>> 32));
        result = 31 * result + (int) (uId ^ (uId >>> 32));
        result = 31 * result + (int) (propertyId ^ (propertyId >>> 32));
        result = 31 * result + (propertyValue != null ? propertyValue.hashCode() : 0);
        return result;
    }

    /**
     *
     */
    public void check() {
        if (propertyValue == null) {
            throw new ProcessException(CodeEnum.ERROR_5035);
        }
        if (propertyId == null) {
            throw new ProcessException(CodeEnum.ERROR_5036);
        }
    }
}
