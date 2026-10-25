import java.util.*; public class CounterTest { static ArrayDeque<int[]> queue=new ArrayDeque<>(); static ArrayDeque<Long> leftClicks=new ArrayDeque<>(),rightClicks=new ArrayDeque<>(); static boolean leftWas,rightWas; static int cps(ArrayDeque<Long> q,boolean d,boolean w){if(d&&!w)q.addLast(0L);return q.size();} static void frame(boolean lmb,boolean rmb){            // module clicks that land between frames still count
            for (int[] c; (c = queue.poll()) != null; ) {
                long now = System.currentTimeMillis();
                if (c[0] == 0 && lmb == leftWas) leftClicks.addLast(now);
                if (c[0] == 1 && rmb == rightWas) rightClicks.addLast(now);
            }
            int l = cps(leftClicks, lmb, leftWas), r = cps(rightClicks, rmb, rightWas);
leftWas=lmb;rightWas=rmb;} public static void main(String[] a){ for(int t=0;t<20;t++){for(int i=0;i<5;i++)queue.add(new int[]{0});frame(true,false);frame(false,false);} if(leftClicks.size()!=20)throw new AssertionError(leftClicks.size()); System.out.println("PASS: queued 100 clicks across 20 ticks; counted "+leftClicks.size()); leftClicks.clear();rightClicks.clear();leftWas=false;rightWas=false;frame(true,true);if(leftClicks.size()!=1||rightClicks.size()!=1)throw new AssertionError();System.out.println("PASS: physical-only clicks retained"); }}