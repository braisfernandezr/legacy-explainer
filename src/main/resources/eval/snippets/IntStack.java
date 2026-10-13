import java.util.Vector;

public class IntStack {
    private Vector items = new Vector();

    public void push(int value) {
        items.addElement(new Integer(value));
    }

    public int pop() {
        Integer top = (Integer) items.lastElement();
        items.removeElementAt(items.size() - 1);
        return top.intValue();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
