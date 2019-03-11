package ipctest;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        integers.add(0, 4);
        integers.add(1, 5);

        System.out.println(integers);
        for (int j = 0; j < 10; j++) {
            int i = Collections.binarySearch(integers, j);
            System.out.println(integers);
            System.out.println(i + "," + j);
            if (i >= 0) continue;
            integers.add(-i - 1, j);
        }

        integers = new LinkedList<>();
        integers.add(0, 2);
        integers.add(1, 3);
        int x = Collections.binarySearch(integers, 1);
        System.out.println(x);
        integers.add(-x - 1, 1);

        System.out.println(integers);
        System.out.println();


    }


}

class Node {
    int v;
    Node next;

    Node(int v) {
        this.v = v;
    }

    @Override
    public String toString() {
        return "Node{" +
                "v=" + v +
                '}';
    }
}

class TTTT {
    public static void middle(Node header) {
        if (header == null || header.next == null)
            return;
        Node slow = header, fast = header;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        System.out.println(slow);

    }

    public static void main(String[] args) {
        Node head = new Node(1);
        Node tail = head;
        tail.next = new Node(2);
        tail = tail.next;
        tail.next = new Node(3);
        tail = tail.next;
        tail.next = new Node(4);
        tail = tail.next;
        tail.next = new Node(5);
        tail = tail.next;
        tail.next = new Node(6);
        tail = tail.next;
        tail.next = new Node(7);
        tail = tail.next;
        tail.next = new Node(7);

        middle(head);


        LinkedList<Node> linkedList = new LinkedList<>();

        while (head != null) {
            System.out.println(head.v);
            linkedList.addLast(head);
            head = head.next;
        }

        System.out.println(linkedList);

        System.out.println(linkedList.pollFirst());
        System.out.println(linkedList.pollLast());


        System.out.println(linkedList.pollFirst());
        System.out.println(linkedList.pollLast());


        System.out.println(linkedList.pollFirst());
        System.out.println(linkedList.pollLast());


    }
}


class Main {
    public static void getRepeateNum(int[] num) {
        int NumChange;
        System.out.println("重复数字是：");
        for (int index = 0; index < num.length; index++) {
            while (num[index] != index) {
                if (num[index] == num[num[index]]) {
                    System.out.print(num[index] + " ");
                    break;
                } else {
                    NumChange = num[num[index]];
                    num[num[index]] = num[index];
                    num[index] = NumChange;
                }
            }
        }
    }

    public static void main(String[] args) {

        int[] num = new int[]{1, 3, 2, 4, 3};   //数组长度可以自己定义
        getRepeateNum(num);
    }
}

class Num {
    int i = 1;
    boolean flag = true;
}

class PrintOdd implements Runnable{
    Num num;

    PrintOdd(Num num) {
        this.num = num;
    }


    @Override
    public void run() {
        while (num.i < 100) {
            synchronized (num) {
                if (num.flag) {
                    System.out.println("奇数："+num.i);
                    num.i += 1;
                    num.flag = false;
                    num.notify();
                }else{
                    try {
                        num.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

class PrintOu implements Runnable{
    Num num;

    PrintOu(Num num) {
        this.num = num;
    }


    @Override
    public void run() {
        while (num.i < 100) {
            synchronized (num) {
                if (num.flag) {
                    try {
                        num.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("outshu:" + num.i);
                    num.flag = true;
                    num.i++;
                    num.notify();
                }
            }
        }
    }
}

class Main1 {
    public static void main(String[] args) throws InterruptedException {
        Num num = new Num();
        PrintOdd printOdd = new PrintOdd(num);
        PrintOu printOu = new PrintOu(num);
        Thread thread = new Thread(printOdd);
        Thread thread1 = new Thread(printOu);
        thread.start();
        thread1.start();
        thread.join();
        thread1.join();

    }
}
