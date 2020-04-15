import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class test {
    public static void main(String[] args) {
        System.out.println("第三次提交");
        System.out.println("第二次提交");
        File file = new File("E:\\plugins");
        File[] files = file.listFiles();
        for (File file1 : files) {
            System.out.println(file1.getName().split("\\\\")[file1.getName().split("\\\\").length-1].split("\\.")[0]);
        }

    }
    public static int[] test(int[] arr)
    {
        System.out.println(arr[0]);
        if (arr.length!=1)
        {
            int[] narr = Arrays.copyOfRange(arr, 1, arr.length);
            return test(narr);
        }
        else return null;

    }
    public static  long j(int n)
    {
        if (n==1)
            return 1;
        else if (n==2)
            return 1;
    else if (n>2)
         return j(n-1)+j(n-2);
    else return 0;
    }
    public static void printZ(int n,int x,int y)
    {
        int[][] ints = new int[x][y];
        for (int i = 1; i <= n; i++) {

        }
    }
}
