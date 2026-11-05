// Platform pieces the shared Java runtime needs on iOS: glyph rasterising and MPEG-1 decoding.
#include <CoreText/CoreText.h>
#include <CoreGraphics/CoreGraphics.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#define PL_MPEG_IMPLEMENTATION
#include "pl_mpeg.h"
static CTFontRef font_for(const char *name,float size){CFStringRef n=CFStringCreateWithCString(NULL,name,kCFStringEncodingUTF8);CTFontRef f=CTFontCreateWithName(n,size,NULL);CFRelease(n);return f;}
void circus_font_metrics(const char *name,float size,float *out){CTFontRef f=font_for(name,size);out[0]=CTFontGetAscent(f);out[1]=CTFontGetDescent(f);out[2]=CTFontGetLeading(f);CFRelease(f);}
// Draws one code point in an 8-bit coverage buffer; row 0 is the top. Returns the advance.
float circus_glyph(const char *name,float size,int cp,unsigned char *mask,int w,int h,float x,float baseline){
    CTFontRef f=font_for(name,size);UniChar u[2];CFIndex n=1;if(cp>0xFFFF){cp-=0x10000;u[0]=0xD800+(cp>>10);u[1]=0xDC00+(cp&0x3FF);n=2;}else u[0]=(UniChar)cp;
    CFStringRef s=CFStringCreateWithCharacters(NULL,u,n);const void *k[]={kCTFontAttributeName,kCTForegroundColorFromContextAttributeName};const void *v[]={f,kCFBooleanTrue};
    CFDictionaryRef a=CFDictionaryCreate(NULL,k,v,2,&kCFTypeDictionaryKeyCallBacks,&kCFTypeDictionaryValueCallBacks);CFAttributedStringRef as=CFAttributedStringCreate(NULL,s,a);CTLineRef l=CTLineCreateWithAttributedString(as);
    float advance=(float)CTLineGetTypographicBounds(l,NULL,NULL,NULL);
    if(mask){CGColorSpaceRef g=CGColorSpaceCreateDeviceGray();CGContextRef c=CGBitmapContextCreate(mask,w,h,8,w,g,(CGBitmapInfo)kCGImageAlphaNone);CGContextSetGrayFillColor(c,1,1);CGContextSetShouldAntialias(c,true);CGContextSetTextPosition(c,x,baseline);CTLineDraw(l,c);CGContextRelease(c);CGColorSpaceRelease(g);}
    CFRelease(l);CFRelease(as);CFRelease(a);CFRelease(s);CFRelease(f);return advance;
}
long circus_mpeg_open(const char *path){plm_t *p=plm_create_with_filename(path);if(!p)return 0;if(!plm_has_headers(p)){plm_destroy(p);return 0;}plm_set_audio_enabled(p,0);plm_set_loop(p,0);return (long)p;}
void circus_mpeg_size(long h,int *out){plm_t *p=(plm_t*)h;out[0]=plm_get_width(p);out[1]=plm_get_height(p);}
double circus_mpeg_next(long h,int *bgra,int stride){plm_t *p=(plm_t*)h;plm_frame_t *f=plm_decode_video(p);if(!f)return -1;if(bgra)plm_frame_to_bgra(f,(uint8_t*)bgra,stride);return f->time;}
void circus_mpeg_close(long h){plm_destroy((plm_t*)h);}
int circus_mpeg_audio_wav(const char *path,const char *wav){
    plm_t *p=plm_create_with_filename(path);if(!p)return -1;if(!plm_has_headers(p)||plm_get_num_audio_streams(p)<1){plm_destroy(p);return 0;}plm_set_video_enabled(p,0);plm_set_audio_enabled(p,1);plm_set_loop(p,0);int rate=plm_get_samplerate(p);
    FILE *o=fopen(wav,"wb");if(!o){plm_destroy(p);return -2;}unsigned char h[44]={0};fwrite(h,1,44,o);unsigned total=0;short buf[PLM_AUDIO_SAMPLES_PER_FRAME*2];plm_samples_t *s;
    while((s=plm_decode_audio(p))){for(unsigned i=0;i<s->count*2;i++){float v=s->interleaved[i];buf[i]=(short)(v>1?32767:v<-1?-32768:v*32767);}fwrite(buf,2,s->count*2,o);total+=s->count*4;}
    #define L32(a,v) do{(a)[0]=(v);(a)[1]=(v)>>8;(a)[2]=(v)>>16;(a)[3]=(v)>>24;}while(0)
    memcpy(h,"RIFF",4);L32(h+4,36+total);memcpy(h+8,"WAVEfmt ",8);h[16]=16;h[20]=1;h[22]=2;L32(h+24,rate);L32(h+28,rate*4);h[32]=4;h[34]=16;memcpy(h+36,"data",4);L32(h+40,total);fseek(o,0,SEEK_SET);fwrite(h,1,44,o);fclose(o);plm_destroy(p);return (int)(total/4);
}
