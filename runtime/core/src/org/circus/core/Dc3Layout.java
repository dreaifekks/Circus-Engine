package org.circus.core;

/** Where a D.C.III build keeps each kind of scene surface.

 *  The three builds that share the DD host compile the same scene structure from the same
 *  sources, but each one lays the logical image slots out differently: D.C.III D.D. gives
 *  the cast ten slots and puts the shared UI surfaces at 36, With You gives it eight and
 *  puts them at 28, P.P. gives it eight and puts them at 21 - and the scripts address each
 *  surface by its own build's number, so none of these ranges can be inherited.

 *  The values come from the resources each build's own scripts load into the slots: the
 *  cast takes BST\, the portrait FACE\, the petal sheet the sakura effect, the fog layer
 *  the fog effect, the wipe mask MSK\, and the UI surfaces are the ones its UI quads ask
 *  for and its release loop spares - DC3DD keeps 36..42 (424100) and DC3WY keeps 28..34
 *  (41EBA6), both checked against the executable.
 */
public final class Dc3Layout {
    /** First slot of the shared UI surfaces: message window, ADV menu chip, cursor. */
    public final int uiBase;
    /** Slot the speaker portrait is loaded into; the build keeps a second one after it. */
    public final int face;
    /** Slot that is switched off when the fog effect ends. */
    public final int fog;
    /** Slot holding the drifting cloud sheet; P.P. ships no cloud art, so it stays empty. */
    public final int clouds;
    /** Slot holding the falling-petal sheet. */
    public final int petals;
    /** Slot holding the wipe mask. */
    public final int mask;
    /** First cast slot. The main cast runs upward from here. */
    public final int spriteFirst;
    /** How many cast members the build can show at once. */
    public final int spriteCount;
    /** Distance from a main cast slot to the slot that repeats its pose on top of it. */
    public final int companionDelta;
    /** Slot drawn above the whole cast and released with it. */
    public final int overlay;
    /** Slots that take a cast member in the extras screens and need its base pose. */
    public final int chipFirst, chipLast;
    /** Draw order: a cast slot, or a negative -(petal group + 1). */
    public final int[] order;

    private Dc3Layout(int uiBase,int face,int fog,int clouds,int petals,int mask,int spriteFirst,int spriteCount,
                      int companionDelta,int overlay,int chipFirst,int chipLast,int[] order) {
        this.uiBase=uiBase;this.face=face;this.fog=fog;this.clouds=clouds;this.petals=petals;this.mask=mask;
        this.spriteFirst=spriteFirst;this.spriteCount=spriteCount;this.companionDelta=companionDelta;
        this.overlay=overlay;this.chipFirst=chipFirst;this.chipLast=chipLast;this.order=order;
    }

    public static final Dc3Layout DD=new Dc3Layout(36,30,25,26,27,21,4,5,5,24,19,23,
        new int[]{1,-1,4,9,-2,5,10,-3,6,11,-4,7,12,8,13,-5,24,25});
    public static final Dc3Layout WY=new Dc3Layout(28,25,20,21,22,16,2,4,4,23,14,17,
        new int[]{1,-1,2,6,-2,3,7,-3,4,8,-4,5,9,-5,23,20});
    public static final Dc3Layout PP=new Dc3Layout(21,18,15,17,16,12,2,4,4,17,10,12,
        new int[]{1,-1,2,6,-2,3,7,-3,4,8,-4,5,9,-5,17,15});

    public static Dc3Layout of(String profileId){
        if(profileId.equals("dc3wy-a09f"))return WY;
        if(profileId.equals("dc3pp-9872"))return PP;
        return DD;
    }
    /** True for the slots the cast occupies, including the pose-repeat block. */
    public boolean cast(int slot){return slot>=spriteFirst&&slot<spriteFirst+2*spriteCount;}
    /** True for the main cast slots, the ones a character command may address. */
    public boolean lead(int slot){return slot>=spriteFirst&&slot<spriteFirst+spriteCount;}
    public int companion(int slot){return slot+companionDelta;}
    /** True for the extras slots that take a cast member and need its base pose. */
    public boolean chip(int slot){return slot>=chipFirst&&slot<=chipLast;}
    public boolean isPetal(int entry){return entry<0;}
    public int petalGroup(int entry){return -entry-1;}
}
