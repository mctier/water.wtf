public class ScopedMaterialTest {
static class DrawContext {}
enum Mode {LIQUID,TINTED}
static Mode mode=Mode.LIQUID;
static Runnable settings=()->{};
static int blurIterations=7;
static float cardMaterial=0,blurRadius=9,blurDownscale=.25f,noise=.4f,refractionPower=.7f,tintR=.1f,tintG=.2f,tintB=.3f,tintStrength=.6f,chroma=.9f,darkness=.8f;
static boolean fail;
static void flush(DrawContext c) { if(cardMaterial!=1||mode!=Mode.TINTED||settings!=null) throw new AssertionError();if(fail)throw new IllegalStateException(); }
    public static void flushCard(DrawContext ctx, int tint, float blur, boolean reducedMotion) {
        float previousCardMaterial = cardMaterial;
        Runnable previousSettings = settings;
        Mode previousMode = mode;
        int previousIterations = blurIterations;
        float previousRadius = blurRadius, previousDownscale = blurDownscale;
        float previousNoise = noise, previousRefraction = refractionPower;
        float previousR = tintR, previousG = tintG, previousB = tintB;
        float previousStrength = tintStrength, previousChroma = chroma, previousDarkness = darkness;
        try {
            cardMaterial = 1f;
            settings = null;
            mode = Mode.TINTED;
            blurRadius = Float.isFinite(blur) ? Math.max(0f, Math.min(24f, blur)) : 10f;
            blurIterations = blurRadius > 0.5f ? 2 : 0;
            blurDownscale = 0.5f;
            noise = 0.012f;
            refractionPower = reducedMotion ? 0.08f : 0.32f;
            tintR = ((tint >>> 16) & 255) / 255f;
            tintG = ((tint >>> 8) & 255) / 255f;
            tintB = (tint & 255) / 255f;
            tintStrength = 0.22f;
            chroma = reducedMotion ? 0f : 0.0008f;
            darkness = 0.02f;
            flush(ctx);
        } finally {
            cardMaterial = previousCardMaterial;
            mode = previousMode;
            blurIterations = previousIterations;
            blurRadius = previousRadius;
            blurDownscale = previousDownscale;
            noise = previousNoise;
            refractionPower = previousRefraction;
            tintR = previousR;
            tintG = previousG;
            tintB = previousB;
            tintStrength = previousStrength;
            chroma = previousChroma;
            darkness = previousDarkness;
            settings = previousSettings;
        }
    }

public static void main(String[] args){
Runnable original=settings;
for(boolean failure:new boolean[]{false,true}){ fail=failure;try{flushCard(new DrawContext(),0xff8c135e,4,false);}catch(IllegalStateException expected){}
if(cardMaterial!=0||mode!=Mode.LIQUID||settings!=original||blurIterations!=7||blurRadius!=9||blurDownscale!=.25f||noise!=.4f||refractionPower!=.7f||tintR!=.1f||tintG!=.2f||tintB!=.3f||tintStrength!=.6f||chroma!=.9f||darkness!=.8f)throw new AssertionError("state leak");}
System.out.println("MODIFIED PASS: scoped Cards state restored on success and exception");}}
