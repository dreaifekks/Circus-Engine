package ios.awt;
public final class GraphicsEnvironment {
    private static final GraphicsEnvironment LOCAL=new GraphicsEnvironment();
    public static GraphicsEnvironment getLocalGraphicsEnvironment(){return LOCAL;}
    public String[] getAvailableFontFamilyNames(){return new String[]{"Hiragino Sans","Hiragino Kaku Gothic ProN"};}
}
