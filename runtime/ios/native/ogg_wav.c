// Decodes an in-memory Ogg Vorbis stream to a 16-bit WAV file.
#define STB_VORBIS_NO_PUSHDATA_API
#define STB_VORBIS_NO_STDIO
#include "stb_vorbis.c"
#include <stdio.h>
static void le32(unsigned char *p,unsigned v){p[0]=v;p[1]=v>>8;p[2]=v>>16;p[3]=v>>24;}
static void le16(unsigned char *p,unsigned v){p[0]=v;p[1]=v>>8;}
int circus_ogg_to_wav(const unsigned char *ogg,int length,const char *path){
    int channels=0,rate=0;short *pcm=NULL;int samples=stb_vorbis_decode_memory(ogg,length,&channels,&rate,&pcm);
    if(samples<=0||!pcm)return samples<0?samples:-100;
    unsigned bytes=(unsigned)samples*channels*2;unsigned char h[44]={'R','I','F','F',0,0,0,0,'W','A','V','E','f','m','t',' ',16,0,0,0,1,0};
    le32(h+4,36+bytes);le16(h+22,channels);le32(h+24,rate);le32(h+28,rate*channels*2);le16(h+32,channels*2);le16(h+34,16);memcpy(h+36,"data",4);le32(h+40,bytes);
    FILE *f=fopen(path,"wb");int ok=f&&fwrite(h,1,44,f)==44&&fwrite(pcm,1,bytes,f)==bytes;if(f)fclose(f);free(pcm);return ok?samples:-101;
}
