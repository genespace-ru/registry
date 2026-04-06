package github;

import ru.genespace.dockstore.ORCIDHelper;
import ru.genespace.dockstore.OrcidAuthor;

public class TestORCIDHelper
{

    public static void main(String[] args)
    {
        ORCIDHelper helper = new ORCIDHelper( "f59f8724-0926-41a1-b79f-8f776c2c4719" );
        OrcidAuthor author = new OrcidAuthor( "0009-0008-6452-9030" );
        helper.fillOrcidInfo( author );
        System.out.println( author );

    }

}
