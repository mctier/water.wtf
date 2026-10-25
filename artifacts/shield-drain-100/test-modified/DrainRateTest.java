public class DrainRateTest { double drainAccum; int lastDrainTick=-1; static class Setting { int getValue(){return 100;} } Setting shieldDrainCps=new Setting();    private int drainBurst(int tick) {
        if (tick == lastDrainTick) return 0;
        lastDrainTick = tick;
        drainAccum = Math.min(5.0, drainAccum + Math.max(0, Math.min(100, shieldDrainCps.getValue())) / 20.0);
        int burst = Math.min(5, (int) Math.floor(drainAccum));
        drainAccum -= burst;
        return burst;
    }public static void main(String[] a){ DrainRateTest x=new DrainRateTest(); int sum=0; for(int t=0;t<20;t++){int n=x.drainBurst(t); if(n>5)throw new AssertionError(); sum+=n; if(x.drainBurst(t)!=0)throw new AssertionError();} if(sum!=100)throw new AssertionError(sum); System.out.println("PASS: "+sum+" attempts / 20 ticks; max 5 per tick; duplicate ticks blocked"); }}