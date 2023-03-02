package stocker.dialog;

/**
 * An interface defining an object which is able to receive results from a search at the remote data provider.
 * 
 * @author Marc S. Schneider
 */
public interface ISearchDataReceiver {
	/**
	 * To be called as soon as the search data from the remote provider is ready. It should process the data in 
	 * an appropriate way.
	 * @param data the search results
	 */
	public void searchDataReady(String[][] data); 
}
