package it.gepo.engine;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.item.ItemProcessor;
import org.tempuri.LeggiMsgQueueResponse;

public class CustomProcessorBiver implements ItemProcessor<Map<String, Object>, Map<String, Object>> {
	private DataSource dataSource;
	private static boolean firstSearch = true;

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	String funzione;
	String istituto;
	static String testo = "";
	static boolean first = true;	
	String cdDato;
	String dato;
	String rapporto = null;
	String filiale;
	String tipoModulo;
	String barcode = "";
	
	public Map<String, Object> process(Map<String, Object> item) throws Exception {
		firstSearch = true;
		String barcode = (String) item.get("BARCODE");
		funzione = "IN";
		istituto = "19";		
		tipoModulo = barcode.substring(0, 6);

		filiale = barcode.substring(6, 9);

		rapporto = barcode.substring(9, 17);
		if (tipoModulo.equals("000202")
				|| tipoModulo.equals("000203")) {

			cdDato = "1964";

			dato = "S";

			if (tipoModulo.equals("000202")) {
				System.out.println("Mod0202E;" + filiale + ";" + rapporto
						+ ";" + cdDato + ";" + dato);

			} else if(tipoModulo.equals("000203")){
				System.out.println("Mod0203E;" + filiale + ";" + rapporto
						+ ";" + cdDato + ";" + dato);

			}
			aggiornaDatoAggiuntivo(istituto, "P", funzione, "1",
					filiale, rapporto, "0", cdDato, dato);
		} else if (tipoModulo.equals("000201")) {

			cdDato = "1965";

			dato = "N";

			System.out.println("Mod0201E;" + filiale + ";" + rapporto
					+ ";" + cdDato + ";" + dato);
			aggiornaDatoAggiuntivo(istituto, "P", funzione, "1",
					filiale, rapporto, "0", cdDato, dato);

		} else if (tipoModulo.equals("000200")) {

			cdDato = "1965";

			dato = "S";

			System.out.println("Mod0200E;" + filiale + ";" + rapporto
					+ ";" + cdDato + ";" + dato);

			aggiornaDatoAggiuntivo(istituto, "P", funzione, "1",
					filiale, rapporto, "0", cdDato, dato);


		}
		
		return item;
	}
	
	public static void aggiornaDatoAggiuntivo(String c_istituto,
			String c_ambiente, String funzione, String n_servizio,
			String n_filiale, String n_rapp, String c_estinto, String cdDato,
			String dato) throws Exception {

		// CHIAMATA ALLA FUNZIONE ANF443
		String rappDati = null;
		LeggiMsgQueueResponse mqResult = new LeggiMsgQueueResponse();

		try {

			rappDati = funzione // funzione
					+ c_estinto
					+ StringUtils.leftPad(n_servizio, 2, '0')
					+ StringUtils.leftPad(n_filiale.toString(), 3, '0')
					+ StringUtils.leftPad(n_rapp.toString(), 8, '0')
					+ StringUtils.leftPad(cdDato.toString(), 5, "0")
					+ StringUtils.rightPad(dato.toString(), 120, " ");
			System.out.println(rappDati);
			mqResult = org.tempuri.InterrogaAnagraficaMQ.DatiAggiuntiviSearch(
					"ANF443", c_istituto, c_ambiente, rappDati);

			if (!mqResult.getLeggiMsgQueueResult().equals(
					"$CompCode=0;$ReasonCode=0;$ReasonDescription=MQRC_NONE;")) {
				throw new Exception(mqResult.getLeggiMsgQueueResult());
			}

			if (mqResult.getVettBuffer() == null
					|| mqResult.getVettBuffer().length == 0) {
				throw new Exception("Webservice MQ - Nessuna Risposta");
			}
			/*
			 * else if(mqResult.getVettBuffer().length > 1) { throw new
			 * Exception( "Webservice MQ - Risposta multipla NON prevista"); }
			 */

			else if (!mqResult.getVettBuffer()[0].substring(105, 106).equals(
					"0")) {
				System.out.println(mqResult.getVettBuffer()[0]);

				// Se non trovo il conto "Aperto" provo a cercare nei conti
				// "Estinti"
				// Se non lo trova neanche nei conti "Estinti" aggiungo l'errore
				// al testo della mail e vado avanti nel leggere il file
				if (first
						&& !mqResult.getVettBuffer()[0]
								.contains("SQLCODE  803")) {
					first = false;
					aggiornaDatoAggiuntivo(c_istituto, "P", funzione, "1",
							n_filiale, n_rapp, "1", cdDato, dato);

				} else {
					if (!mqResult.getVettBuffer()[0].contains("SQLCODE  803")) {
						testo += "Rapporto: " + n_rapp + " Filiale: "
								+ n_filiale + " Codice Dato non aggiornato: "
								+ cdDato + " Dato non aggiornato: " + dato
								+ " Messaggio di errore:"
								+ mqResult.getVettBuffer()[0] + "\r\n";
					} else {
						System.out.println("UPDATE MQ");
						aggiornaDatoAggiuntivo(c_istituto, "P", "UP", "1",
								n_filiale, n_rapp, c_estinto, cdDato, dato);

					}
				}
			} else {
				System.out.println(mqResult.getVettBuffer()[0]);
			}
			// datiMq = new DatiAggiuntiviMQ(mqResult.getVettBuffer());
			// logger.debug(mqResult.getVettBuffer()[0]);

		} catch (Exception e) {
			System.out.println(e.getMessage());
			if (!mqResult.getVettBuffer()[0].contains("SQLCODE  803")) {
				testo += "Rapporto: " + n_rapp + " Filiale: " + n_filiale
						+ " Codice Dato non aggiornato: " + cdDato
						+ " Dato non aggiornato: " + dato
						+ " Messaggio di errore:" + e.getMessage() + "\r\n";
			}
			// throw new Exception(e.getMessage());

		}

	}
	private static String getTodayFormatted() {
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		Date data = new Date();
		Calendar c = Calendar.getInstance();
		c.set(2019, 5, 1);
		String today = formatter.format(c.getTime());
		return today;
	}
}
