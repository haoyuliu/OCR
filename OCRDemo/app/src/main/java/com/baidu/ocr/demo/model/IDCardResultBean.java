package com.baidu.ocr.demo.model;

import com.baidu.ocr.sdk.model.IDCardResult;

/**
 * @Author: Nemo
 * @Date: 2019-12-23
 * @Description
 */
public class IDCardResultBean extends IDCardResult {
    private String photo;
    private String photo_location;

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getPhoto_location() {
        return photo_location;
    }

    public void setPhoto_location(String photo_location) {
        this.photo_location = photo_location;
    }
}
