package org.circus.core;

import java.io.IOException;
import java.util.*;

/** Observed DD/DM layout. Extra numeric fields remain opaque until independently proved. */
public final class NamesCatalog {
    public static final String[] CATEGORIES={"system_scripts","music","sound","images","scenario_scripts"};
    private static final int[] STRIDES={32,48,36,32,32};
    public static final class Entry {
        public final int index;
        public final byte[] nameBytes,extra;
        public final String name;
        Entry(int index,byte[] nameBytes,byte[] extra){this.index=index;this.nameBytes=nameBytes;this.extra=extra;name=new String(nameBytes,Binary.SJIS);}
    }
    private final Map<String,List<Entry>> tables=new LinkedHashMap<>();
    public NamesCatalog(byte[] data)throws IOException {this(data,false);}
    public NamesCatalog(byte[] data,boolean legacy)throws IOException {
        // The oldest build (温泉編) stores its scenario names without the extra 60 bytes.
        if(legacy)try{parse(data,true,new int[]{128,192,132,128,188});}catch(IOException older){tables.clear();parse(data,true,new int[]{128,192,132,128,128});}
        else parse(data,false,STRIDES);
    }
    private void parse(byte[] data,boolean legacy,int[] strides)throws IOException {
        int nameWidth=legacy?128:32;
        int p=0;
        for(int t=0;t<CATEGORIES.length;t++){
            long count=Binary.u32(data,p);p+=4;
            if(count>100000)throw new IOException("NAMES table count limit");
            int stride=strides[t];Binary.range(p,count*stride,data.length);
            List<Entry> entries=new ArrayList<>();
            for(int i=0;i<count;i++){
                int at=p+i*stride,end=at;
                while(end<at+nameWidth&&data[end]!=0)end++;
                if(end==at+nameWidth)throw new IOException("NAMES missing name terminator");
                entries.add(new Entry(i,Arrays.copyOfRange(data,at,end),Arrays.copyOfRange(data,at+nameWidth,at+stride)));
            }
            tables.put(CATEGORIES[t],Collections.unmodifiableList(entries));p+=(int)count*stride;
        }
        if(p!=data.length)throw new IOException("Unknown NAMES trailing data");
    }
    public List<Entry> table(String category)throws IOException {
        List<Entry> result=tables.get(category);
        if(result==null)throw new IOException("Unknown NAMES category "+category);
        return result;
    }
}
