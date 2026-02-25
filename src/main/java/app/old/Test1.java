package app;

public class Test1 {
    public void method(int x, int y) {
        Box box = new Box();
        box.width = x;
        box.height = y;
        box.area = box.width * box.height;
        int result = box.area * 2;
        if (result > 50) {
            System.out.println("ok");
        }
        else if(box.area > 20) {
            System.out.println("not ok");
        }
    }
}
            