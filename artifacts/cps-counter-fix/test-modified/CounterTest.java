import java.util.*; public class CounterTest { static ArrayDeque<int[]> queue=new ArrayDeque<>(); static ArrayDeque<Long> leftClicks=new ArrayDeque<>(),rightClicks=new ArrayDeque<>(); static boolean leftWas,rightWas; static int cps(ArrayDeque<Long> q,boolean d,boolean w){if(d&&!w)q.addLast(0L);return q.size();} static void frame(boolean lmb,boolean rmb){            // Count every queued click, even on a binding-state transition. Only suppress
            // the extra polled edge when this frame already contains a click for that button.
            boolean simulatedLeft = false, simulatedRight = false;
            for (int[] c; (c = queue.poll()) != null; ) {
                long now = System.currentTimeMillis();
                if (c[0] == 0) { leftClicks.addLast(now); simulatedLeft = true; }
                if (c[0] == 1) { rightClicks.addLast(now); simulatedRight = true; }
            }
            int l = cps(leftClicks, lmb, leftWas || simulatedLeft);
            int r = cps(rightClicks, rmb, rightWas || simulatedRight);
leftWas=lmb;rightWas=rmb;} public static void main(String[] a){ for(int t=0;t<20;t++){for(int i=0;i<5;i++)queue.add(new int[]{0});frame(true,false);frame(false,false);} if(leftClicks.size()!=100)throw new AssertionError(leftClicks.size()); System.out.println("PASS: queued 100 clicks across 20 ticks; counted "+leftClicks.size()); leftClicks.clear();rightClicks.clear();leftWas=false;rightWas=false;frame(true,true);if(leftClicks.size()!=1||rightClicks.size()!=1)throw new AssertionError();System.out.println("PASS: physical-only clicks retained"); }}