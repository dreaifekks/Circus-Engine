package org.circus.core;

import java.util.Random;

/**
 * DC2PC 418AB0, 4190C0: effect 14 cuts the screen into 32 px cells that fly apart over black, one 40 ms step at a time.
 * The exe also stamps a system\eff mask on each cell as it goes; that is not reproduced here. Its rand() seed is unknown,
 * so the cells differ from run to run in both the exe and here.
 */
final class PcShatter {
    static final int COLUMNS=25,ROWS=19;
    private final int pace;
    private final int[] life=new int[COLUMNS*ROWS],delay=new int[life.length],vx=new int[life.length],vy=new int[life.length],t=new int[life.length];
    final int[] x=new int[life.length],y=new int[life.length],alpha=new int[life.length];
    PcShatter(int mode,long seed){
        Random r=new Random(seed);pace=mode==9?327:81; // (speed<<13)/100; only mode 9 asks for speed 4.
        for(int row=0;row<ROWS;row++)for(int col=0;col<COLUMNS;col++){
            int n=row*COLUMNS+col;life[n]=16+r.nextInt(32);alpha[n]=16;x[n]=col<<15;y[n]=row<<15;
            if(mode==5){delay[n]=-(2*row+col);vx[n]=(2-(r.nextBoolean()?8:0))<<10;vy[n]=((r.nextBoolean()?8:0)-2)<<10;} // 418E80
            else if(mode==9){vx[n]=(r.nextInt(16)-8)<<10;vy[n]=(-32-r.nextInt(32))<<10;} // Vista 410E1C
            else if(mode==6){delay[n]=-(Math.abs(9-row)+Math.abs(12-col));vx[n]=(4-(r.nextBoolean()?8:0))<<10;vy[n]=(4-(r.nextBoolean()?8:0))<<10;} // Vista 410D9D
            else{vx[n]=(r.nextBoolean()?1+r.nextInt(32):r.nextInt(32)-32)<<10;vy[n]=(32-(r.nextInt(4)<<4))<<10;} // 418E07
        }
    }
    /** One step; true while any cell is still alive. */
    boolean step(){
        boolean alive=false;
        for(int row=0;row<ROWS;row++)for(int col=0;col<COLUMNS;col++){
            int n=row*COLUMNS+col;
            if(life[n]<=0){life[n]=0;continue;}
            if(delay[n]<0){delay[n]++;alive=true;continue;}
            int now=t[n];
            x[n]=(col<<15)+4*(int)(((long)vx[n]*now)/1024);
            y[n]=(row<<15)-(int)(((long)vy[n]*now)/1024)-(int)(((long)((now*4008L)/1024)*now)/1024);
            if(x[n]>=800<<10||y[n]>=600<<10)life[n]=0;
            life[n]--;t[n]=now+pace;delay[n]++;
            if(life[n]<=16){alpha[n]=Math.max(0,life[n]);if(life[n]<=0)life[n]=0;}
            alive|=life[n]>0;
        }
        return alive;
    }
    /** The number of steps the effect lasts for a given seed. */
    static int ticks(int mode,long seed){PcShatter s=new PcShatter(mode,seed);int n=1;while(s.step()&&n<4096)n++;return n;}
}
