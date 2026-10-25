package com.utilitymod.anubis.module.impl.donutmacros;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;

final class MacroWorker {
   private static final long POLL_MS = 50L;
   private static final long CALL_TIMEOUT_SECONDS = 10L;
   private final String name;
   private volatile Thread thread;

   MacroWorker(String name) {
      this.name = name;
   }

   synchronized void start(Runnable body, Consumer<Throwable> crashed) {
      this.stop();
      Thread worker = new Thread(() -> {
         try {
            body.run();
         } catch (MacroWorker.Cancelled var18) {
         } catch (Throwable failure) {
            if (this.thread == Thread.currentThread()) {
               crashed.accept(failure);
            }
         } finally {
            synchronized (this) {
               if (this.thread == Thread.currentThread()) {
                  this.thread = null;
               }
            }
         }
      }, this.name);
      worker.setDaemon(true);
      this.thread = worker;
      worker.start();
   }

   synchronized void stop() {
      Thread worker = this.thread;
      this.thread = null;
      if (worker != null && worker != Thread.currentThread()) {
         worker.interrupt();
      }
   }

   boolean running() {
      return this.thread != null;
   }

   void check() {
      if (this.thread != Thread.currentThread() || Thread.currentThread().isInterrupted()) {
         throw new MacroWorker.Cancelled();
      }
   }

   void sleep(long ms) {
      this.check();

      try {
         Thread.sleep(Math.max(0L, ms));
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new MacroWorker.Cancelled();
      }

      this.check();
   }

   void ticks(int ticks) {
      this.sleep(ticks * 50L);
   }

   <T> T call(Supplier<T> task) {
      this.check();
      CompletableFuture<T> future = MinecraftClient.getInstance().submit(task);

      try {
         return future.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new MacroWorker.Cancelled();
      } catch (ExecutionException e) {
         Throwable cause = e.getCause();
         if (cause instanceof RuntimeException runtime) {
            throw runtime;
         } else if (cause instanceof Error error) {
            throw error;
         } else {
            throw new IllegalStateException(cause);
         }
      } catch (TimeoutException e) {
         future.cancel(false);
         throw new IllegalStateException(LT.L1);
      }
   }

   void run(Runnable task) {
      this.call(() -> {
         task.run();
         return Boolean.TRUE;
      });
   }

   boolean waitFor(BooleanSupplier condition, long timeoutMs) {
      long end = System.currentTimeMillis() + timeoutMs;

      while (!this.call(condition::getAsBoolean)) {
         if (System.currentTimeMillis() >= end) {
            return false;
         }

         this.sleep(50L);
      }

      return true;
   }

   static final class Cancelled extends RuntimeException {
      Cancelled() {
         super(null, null, false, false);
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0255,0x4bb0,0x0be5,0x1d51,0x49f1,0x404c,0x2099,0xc04f,0x1001,0x663a,0x795a,0x12b4,0x24b9,0x5de9,0x04df,0x1d4c,0xe7cb,0xae7e,0xeaab,0x0695,0x9e44,0xbe60,0xb64a,0x2caf}, new char[]{0x0bb1,0x6652,0x2cca,0x797a,0x4bd7,0xeaca,0x0238,0xe7ae,0xc06f,0x06f1,0x4025,0xae1a,0x2499,0xbe0e,0x499e,0xb63e,0x04ff,0x1d3e,0x20fc,0x12c7,0x9e34,0x1d3e,0x106f,0x5d8d}).toString();
    }
}
