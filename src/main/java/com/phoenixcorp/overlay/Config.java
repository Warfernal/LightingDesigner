package com.phoenixcorp.overlay;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    public Integer ocrX, ocrY, ocrW, ocrH;
    public String tessDataPath;
    public String tessLang;

    @JsonIgnore
    public Rectangle getOcrCaptureArea() {
        if (ocrX==null || ocrY==null || ocrW==null || ocrH==null) return null;
        return new Rectangle(ocrX, ocrY, ocrW, ocrH);
    }
    @JsonIgnore
    public void setOcrCaptureArea(Rectangle r) {
        if (r == null) { ocrX=ocrY=ocrW=ocrH=null; return; }
        ocrX=r.x; ocrY=r.y; ocrW=r.width; ocrH=r.height;
    }

    public String getTessDataPath(){ return tessDataPath; }
    public String getTessLang(){ return tessLang; }
}
