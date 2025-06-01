package test;

public class DeadLockTest {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    public void testDeadlock() {
        Thread t1 = new Thread(() -> {
            synchronized(lock1) {
                System.out.println("Thread 1: locked lock1");
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                synchronized(lock2) {
                    System.out.println("Thread 1: locked lock2");
                }
            }
        });

        Thread t2 = new Thread(() -> {
            synchronized(lock2) {
                System.out.println("Thread 2: locked lock2");
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                synchronized(lock1) {
                    System.out.println("Thread 2: locked lock1");
                }
            }
        });

        t1.start();
        t2.start();
    }

    public static void main(String[] args) {
        new DeadLockTest().testDeadlock();
    }
}
