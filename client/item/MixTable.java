package client.item;

/**
 * Created by Hong on 2014-06-20.
 * <p/>
 * 6 7 8
 * 3 4 5
 * 0 1 2  각 칸에 아이템이 들어갈경우 ItemMakeOn에 정보 저장하는 것 필요
 */

public class MixTable {
    Item[] ItemMakeOn = new Item[9];

    public void setItemMakeOn(int i, Item item) {
        ItemMakeOn[i] = item;
    }

    public Item getItemMakeOn(int i) {
        return ItemMakeOn[i];
    }
}
