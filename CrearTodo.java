package com.ba.doc.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;  
import org.apache.logging.log4j.LogManager;


import com.ba.doc.content.ContentManagerOracle;
import com.ba.doc.contratos.AciNueva;
import com.ba.doc.contratos.Cancelacion;
import com.ba.doc.contratos.CartaBenefDAP;
import com.ba.doc.contratos.CartaBeneficiario;
import com.ba.doc.contratos.CartaInformativa;
import com.ba.doc.contratos.CartaInformativaDAP;
import com.ba.doc.contratos.CertificadoDAP;
import com.ba.doc.contratos.ContratoDepositos;
import com.ba.doc.contratos.ContratoInternet;
import com.ba.doc.contratos.ContratoMarco;
import com.ba.doc.contratos.DeclaracionJurada;
import com.ba.doc.contratos.DocVinculacion;
import com.ba.doc.contratos.ExportarContrato;
import com.ba.doc.contratos.PDFMerger;
import com.ba.doc.correo.EnviarLatinia;
import com.ba.doc.correo.PlantillaLatinia;
import com.ba.doc.dto.BancoDTO;
import com.ba.doc.dto.CancelacionDTO;

import com.ba.doc.dto.ClienteDTO;
import com.ba.doc.dto.ContratoDTO;
import com.ba.doc.dto.ContratoInternetDTO;
import com.ba.doc.dto.DapDTO;
import com.ba.doc.dto.FullDecJurada;
import com.ba.doc.dto.ProcesoDTO;
import com.ba.doc.dto.TablaInteresDTO;
import com.ba.doc.mobbscan.EnviarMobbScan;
import com.ba.doc.util.Configuracion;
import com.ba.doc.util.DeleteDirVisitor;
import com.ba.doc.util.ManejoArchivo;
import com.ba.doc.util.Utils;
import com.ba.doc.util.Validacion;


public class CrearTodo {
	private static final Logger  log 					= LogManager.getLogger(CrearTodo.class);
	
	private List<String> documentosComprimir = null; //Para plantillas OTP F2F
	
	// CODIGOS
	private static final String CODIGOX = "xxxxxx";
	private static final String CODIGO0 = "000000";
	private static final String CODIGO1 = "000001";
	private static final String CODIGO2 = "000002";
	private static final String CODIGO3 = "000003";
	private static final String CODIGO4 = "000004";
	private static final String CODIGO5 = "000005";
	private static final String CODIGO6 = "000006"; 
	private static final String CODIGO7 = "000006"; 
	
	private static final String NOCONTRATOS = "No hay contratos para enviar por correo - long:";
	private static final String FINALEXITO = "FINALIZADO EXITOSAMENTE";
	private static final String INIVALORES = "Error inicializando valores :";
	private static final String VALIDARTRAMA = "Error validando la trama";
	
	private static final String NAMECANCEL = "Carta_Cancelación_";
	private static final String NAMEBENEFI = "Beneficiarios_";
	
	private static final String CONTRATO_ACT_DATOS = "refActualizaDatosPersonales";
	
	private static final String URL_DIR_ORIGI  = "url.dir.origi";
	private static final String URL_DIR_JASPER = "url.dir.jasper";
	private static final String URL_DIR_COPIA  = "url.dir.copia";
	private static final String URL_DIR_FIRMA  = "url.dir.firma";
	
	private static final String CONTRATO = "contrato";
	private static final String CLIENTE = "cliente";
	
	public CrearTodo(){ /**/ }
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String crearTodos(ProcesoDTO proceso, String hilo){
		
		ClienteDTO cliente 			= new ClienteDTO();
		BancoDTO banco 				= new BancoDTO();
		TablaInteresDTO tabla 		= new TablaInteresDTO();
		ContratoDTO contrato 		= new ContratoDTO();
		DapDTO dap	 				= new DapDTO();
		
		EnviarMobbScan escaneo 		= new EnviarMobbScan();
		FullDecJurada dcl   		= new FullDecJurada();
		CancelacionDTO cancel 		= new CancelacionDTO();
		String detalleContratos 	= "";
		String documentosEmail		= "";
		String respuesta 			= "";
		Map<String, Object > p		= new HashMap<>();
		Map<String, Object > f2f;
		
		Configuracion.cargarPropertiesCMO();
		log.info("{} MDB -> CREANDO VARIOS CONTRATOS ",hilo );
		boolean asignado = Asignar.asignarValores(proceso, cliente, banco, tabla, dap, dcl,cancel) ;
		
		if ( validarTramas(proceso) ){
			  
			if( asignado ){
				
				String []cntLst = cliente.getListaContratos().split(",");
				log.info("{} POSIBLES CONTRATOS [{}]" ,hilo,cntLst.length);
				
				f2f = generarCarpetaTemporalF2F(proceso, cliente);
				log.info("{} SE GENERO CARPETA TEMPORAL PARA F2F",hilo);
				
				
				p.put("proceso", proceso);
				p.put(CONTRATO, contrato);
				p.put("hilo", hilo);
				p.put(CLIENTE, cliente);
				p.put("banco", banco);
				p.put("tabla", tabla);
				p.put("dap", dap);
				p.put("dcl", dcl);
				p.put("cancel", cancel);
				p.put("escaneo", escaneo);

				
				Map<String,String>generarContratos = generandoContratos(cntLst, p);
				int contratos = generarContratos.size();
				if( contratos > 0 ) {
					log.info("{} Se generaron {} contratos ", hilo , contratos );
					
					detalleContratos  = generarContratos.get("respuesta");
					documentosEmail   = generarContratos.get("documentos");
					
					boolean condicion = cliente.getFirmado().equals("Y") || contrato.getAlmacenarCM().equalsIgnoreCase("N") && !isF2FAndTemplates(proceso, cliente); 

					respuesta =     condicion ? metodo1(detalleContratos, documentosEmail, proceso, cliente, dap) : metodo2(f2f, proceso, cliente, hilo, detalleContratos);
					
				}
				else {
					respuesta = Procesar.respuestaErrorTodos(CODIGOX, "No hay contratos para enviar");
				}		
			}
			else{
				respuesta = Procesar.respuestaErrorTodos(CODIGO2, INIVALORES + asignado);
			}
		}
		else{
			respuesta = Procesar.respuestaErrorTodos(CODIGO1, VALIDARTRAMA );
			
		}
		
		mostrarResumen(cliente, contrato, hilo, respuesta);

		log.info("{} Respuesta ----->{}",hilo, respuesta);
		return respuesta;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String crearDeclaracion(ProcesoDTO proceso, String hilo) {
		log.info("{} MDB ->CREANDO DECLARACION JURADA Y FORMULARIO ", hilo);
		ClienteDTO cliente 			= new ClienteDTO();
		BancoDTO banco 				= new BancoDTO();
		FullDecJurada declaracion 	= new FullDecJurada();
		ContratoDTO contrato 		= new ContratoDTO();
		ContratoDTO contrato2 		= new ContratoDTO();
		Map<?, ?> reportParams		= null;
		
		Validacion validar 			= new Validacion();
		
		
		Asignar a 					= new Asignar();
		
		boolean setear 				= false;
		boolean setear2 			= false;
		String respuesta 			= "";		

		JasperParams j 				= new JasperParams();
		boolean validado 			= validar.validarTramaDeclaracion(proceso, hilo);
		if (validado) {
			log.info("{} MDB ->DCL -> TRAMA OK" , hilo);
			boolean asignado =  a.asignarValorDcl(proceso, cliente, banco,declaracion, hilo);
			log.info("{} Se setearon los valores ? {}", hilo ,asignado);
			if (asignado) {
				
				contrato.setCodigoContrato("46");
				contrato2.setCodigoContrato("46");
				
				log.info("{} MDB -> DCL -> CONTRATO A GENERAR DJ Y FRM", hilo);
				
				setear =  setearContrato(proceso, contrato  , hilo, cliente);
				setear2 = setearContrato(proceso, contrato2 , hilo , cliente);
				
				log.info("{} setear2:{}", hilo , setear2);
				
				reportParams = j.setearParametrosDCL(declaracion, cliente, banco,contrato, hilo);
				 
				if (setear && reportParams != null) {
					
					respuesta = crearContrato(proceso, cliente, contrato, reportParams, declaracion, contrato2); 
					
				}
				else {
					respuesta = Procesar.respuestaError(CODIGO3,	"Error seteando parametros");
				}
			}
			else {
				respuesta = Procesar.respuestaError(CODIGO2,	"Error asignando objetos ");
			}
		}
		else {
			respuesta = Procesar.respuestaError(CODIGO1,	"Error validando trama :" + validar);
		}						
		
		return respuesta; 
	}

	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public boolean esCorrecto(String valor, String hilo){
		boolean resp = false;
		try {
			valor = valor.trim();
			if ( valor.equalsIgnoreCase("Y") ||  valor.equalsIgnoreCase("S")  ){
				resp = true;
			}
			else{
				resp = false;
			}
		} catch (Exception e) {
			log.error("{} Error validando flag",hilo);
			resp = false;
		}
		return resp;
		
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	@SuppressWarnings("null")
	public int[] crearArray(String contratos){
		int[] resp = null;
	    String[] cont = contratos.split(",");
	    int size = cont.length;
	    for(int i=0; i<size; i++) {
	    		  resp[i] = Integer.parseInt(cont[i]);
	      }
		return resp;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/

	public boolean setearContrato(ProcesoDTO proceso, ContratoDTO contrato, String hilo , ClienteDTO cliente){
		boolean respuesta 		= false;
		Configuracion conf 		= new Configuracion();
		String sufix 			= "";
	
		try {
			String fechaVencimiento = "";
			Configuracion.cargarPropertiesCMO();
			String urlDap 	  = conf.getParamCMO("url.dir.jasper.dap");
			String urlAci 	  = conf.getParamCMO("url.dir.jasper.aci");
			String urlDcl 	  = conf.getParamCMO(URL_DIR_JASPER);
			String urlCan 	  = conf.getParamCMO("url.dir.jasper.can");
			String urlCxp 	  = conf.getParamCMO("url.dir.jasper.cxp");
			String contratoSufix = proceso.getSufix();
			
				
			switch (contrato.getNumContrato()) {
				/* ----------------------------------------	CANCELACION ---------------------------------------------------*/
				case 21: sufix = "expca.C001";
			 			 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
			 			 break;
			 			 
				case 22: sufix = "expca.C002";
	 			 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
	 			 		 break;
	 			 
				case 23: sufix = "expca.C003";
	 			 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
	 			 		 break;
	 			 
				case 24: sufix = "expca.C004";
	 			 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
	 			 		 break;
			 			
				case 25: sufix = "expca.C005";
	 			 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
	 			 		 break;
	 			 
				case 26: sufix = "expca.C006";
	 			 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
	 			 		 break;
	 			/* ----------------------------------------	CANCELACION ---------------------------------------------------*/
				case 27: sufix = "expca.C007";
		 		 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
				case 28: sufix = "expca.C008";
		 		 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
				case 29: sufix = "expca.C009";
		 		 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
				case 30: sufix = "expca.C010";
		 		 		 contrato.setNombreOriginal(NAMECANCEL+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
				/* ----------------------------------------	CTA DIGITAL ---------------------------------------------------*/
				case 41: sufix = "benef."+contratoSufix;
						 contrato.setNombreOriginal(NAMEBENEFI+cliente.getNumeroUnico()+ ".pdf");
						 break;
								 
				case 42: sufix = "cinfo."+contratoSufix;
						 contrato.setNombreOriginal("CartaInformativa"+cliente.getNumeroUnico()+ ".pdf");
						 break;
								 						 
				case 43: sufix = "cmarc."+contratoSufix;
						 contrato.setNombreOriginal("ContratoMarco_"+cliente.getNumeroUnico()+ ".pdf");
						 break;
						 
				case 44: sufix = "acism."+contratoSufix;
						 contrato.setNombreOriginal("ACISimp_"+cliente.getNumeroUnico()+ ".pdf");
						 fechaVencimiento = conf.getParamCMO("cmo.cod.venc");
						 break;
				
				case 45: sufix = "cdepo."+contratoSufix;
						 contrato.setNombreOriginal("ContDepo_"+cliente.getNumeroUnico()+ ".pdf"); 
						 break;
						 
				case 46: sufix = "decjur."+contratoSufix;
						 contrato.setNombreOriginal("DCL_"+cliente.getNumeroUnico()+ ".pdf");
						 fechaVencimiento = sumarAnios(cliente.getFechaHora(),2, hilo);
						 break;
			/* ----------------------------------------	DAP DIGITAL ---------------------------------------------------*/		 
			
				case 47: sufix = "benef."+contratoSufix;
						 contrato.setNombreOriginal("BeneficiariosDAP_"+cliente.getNumeroUnico()+ ".pdf");
				 		 break;
				 		 
				case 48: sufix = "cinfo."+contratoSufix;
						 contrato.setNombreOriginal("CartaInformativaDAP_"+cliente.getNumeroUnico()+ ".pdf");
				 		 break;
				 		 
				case 49: sufix = "certi."+contratoSufix;
						 contrato.setNombreOriginal("CertificadoDAP_"+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
		 		 		 
				case 50: sufix = "acism."+contratoSufix;
		 		 		 contrato.setNombreOriginal("ACISimplif_"+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break; 
		 		 		 
				case 51: sufix = "renov."+contratoSufix;
		 		 		 contrato.setNombreOriginal("RenovacionDAP_"+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break; 
		 		 		 
				case 52: sufix = "cance."+contratoSufix;
		 		 		 contrato.setNombreOriginal("CancelaciónDAP_"+cliente.getNumeroUnico()+ ".pdf");
		 		 		 break;
		 		 		 
				case 53: sufix = "cinter."+contratoSufix;
				 		 contrato.setNombreOriginal("ContratoInternet_"+cliente.getNumeroUnico()+ ".pdf");
				 		 break; 
				 		 
				 		 
				default: break;
						 
			}
			
			
			contrato.setCodigoProd(	conf.getParamCMO("cmo.cod.producto."+contratoSufix));
			contrato.setTipo(		conf.getParamCMO("cmo.cod.tipo"));
			contrato.setOrigen(		conf.getParamCMO("cmo.cod.origen"));
			contrato.setCanal(		conf.getParamCMO("cmo.cod.canal"));
			
			contrato.setUrlOriginal(conf.getParamCMO(URL_DIR_ORIGI));
			contrato.setUrlCopy(	conf.getParamCMO(URL_DIR_COPIA));
			contrato.setUrlFirma(	conf.getParamCMO(URL_DIR_FIRMA));
			
			String codProducto 			= conf.getParamCMO("cmo.cod.producto."+contratoSufix); 
			String entidad 				= conf.getParamCMO("cmo.enti."+sufix); 
			String codigoDoc 			= conf.getParamCMO("cmo.tipo."+sufix);
			String codProdTipoProd  	= codProducto + codigoDoc;
			String entidadOrg			= conf.getParamCMO("cmo.entorg."+sufix);
			
			contrato.setCodProdTipoProd( codProdTipoProd); 
			contrato.setEntidad( entidad );
			contrato.setCodigoDocu( codigoDoc );
			contrato.setFechaVencimiento(fechaVencimiento);
			Utils.codigoSeguridad(contrato.getNumContrato(),""+cliente.getNumCliente(),cliente.getFecha(), contrato,hilo);	
			contrato.setEntidadOrg(entidadOrg);
			contrato.setUrlDAP(urlDap);
			contrato.setUrlDCL(urlDcl);
			contrato.setUrlACI(urlAci);
			contrato.setUrlCAN( urlCan );
			contrato.setUrlCANEX( urlCxp );
			
			log.info("{} CONF CONTRATO SETEADO" , hilo);
			respuesta = true;
		} 
		catch (Exception e) {
			log.error("{} Error inicializando valores contrato" , hilo);
			respuesta = false;
		}
		return respuesta;
		
	}
	
	
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String makeContract( Map<String, Object> p ) {
		ProcesoDTO proceso 		= (ProcesoDTO) 		p.get("proceso");
		ContratoDTO contrato 	= (ContratoDTO) 	p.get(CONTRATO);
		String hilo 			= (String) 			p.get("hilo");
		ClienteDTO cliente 		= (ClienteDTO) 		p.get(CLIENTE);
		BancoDTO banco 			= (BancoDTO) 		p.get("banco");
		TablaInteresDTO tabla 	= (TablaInteresDTO) p.get("tabla");
		DapDTO dap 				= (DapDTO) 			p.get("dap");
		FullDecJurada dcl 		= (FullDecJurada) 	p.get("dcl");
		CancelacionDTO cancel 	= (CancelacionDTO) 	p.get("cancel");
		
		Configuracion conf 		= new Configuracion();
		Configuracion.cargarPropertiesCMO();
		
		String respuesta		= "";
		
		String urljasper  		= conf.getParamCMO(URL_DIR_JASPER);
		
		boolean setear 			= false;
		File contratoFile		= null;

		CartaBenefDAP cartaDB 	= new CartaBenefDAP();
		CartaBeneficiario cartaB= new CartaBeneficiario();
		CertificadoDAP certif 	= new CertificadoDAP();
		
		AciNueva aci 			= new AciNueva();
		Cancelacion cancelar 	= new Cancelacion();
		JasperParams j = new JasperParams();
		Map<String,String> reportParams = null;
		ContratoInternetDTO contratoInternetDTO;
		
		setear 					= setearContrato(proceso, contrato, hilo, cliente);
		log.info("{} SETEADO ? : {}", hilo,setear);
		
		if (setear) {
			log.info("{} Contrato seteado : {}", hilo, contrato.getNumContrato());
			
			
			switch (contrato.getNumContrato()) {
				case 21:	
				case 22:	
				case 23:    
				case 24:    
				case 25:	
				case 26:	
				case 27:	
				case 28:	
				case 29:	
				case 30:	contratoFile = cancelar.generarContrato( contrato, cancel , hilo );
							break;		
			
			
				case 41:	contratoFile = cartaB.crearCartaBenefDap(contrato, cliente, banco, hilo);
							break;
							
				case 42:	contratoFile = CartaInformativa.cartaInfo(cliente, contrato, tabla, hilo);
							break;
							
				case 43:	contratoFile = ContratoMarco.ContratoMarcoOperacionesBancarias(	cliente, banco, contrato, hilo);
							break;
						

					
				case 45:	contratoFile = ContratoDepositos.contratoDepositos(cliente,	banco, contrato, tabla, hilo); // a este se le quitó lo de comisiones es de revisarlo
							break;
							
				case 46:	reportParams = j.setearParametrosDCL(dcl, cliente, banco,contrato, hilo);
							contratoFile = dclUnificado(contrato, reportParams, cliente, dcl, hilo, urljasper);
							break;
						
				case 47:	contratoFile = cartaDB.crearCartaBenefDap(contrato, cliente, banco, hilo); 
							break;
							
				case 48:    contratoFile = CartaInformativaDAP.cartaInfo(cliente, banco, contrato, dap, hilo);
							break;
	
				case 49:    reportParams = j.setearParametrosCertDap(cliente, banco, contrato, dap, hilo);
							contratoFile = certif.generarPFDCertificadoDAP(reportParams , cliente, banco, contrato, dap, hilo );
							break;

				case 44:	
				case 50:	reportParams = j.setearParametrosACI( cliente, contrato, hilo);
							contratoFile = aci.generarPFD(reportParams, cliente, contrato, hilo);
							break;

				case 51:    reportParams = j.setearParamsDapRenovar(cliente, banco, contrato, dap, hilo);
							contratoFile = certif.generarPFDCertificadoDAP(reportParams , cliente, banco, contrato, dap, hilo );
							break;
				
				case 52:    reportParams = j.setearParamsDapCancelar(cliente,  contrato, dap, hilo);
							contratoFile = certif.generarPFDCertificadoDAP(reportParams , cliente, banco, contrato, dap, hilo );
							break;
							
				case 53:    contratoInternetDTO = ContratoInternet.setearParamsContratoInternet(proceso);
							contratoFile = ContratoInternet.generarContrato(contratoInternetDTO, contrato,hilo);
							break;
				default: 	contratoFile = null;
							break;
			
			}
			
			if(cliente.getAlmacenarCM().equals("N")){
				respuesta = noAlmacenaContent(contratoFile, contrato, proceso, cliente);
			} 
			else {
				respuesta = siAlmacenaContent(contratoFile, contrato, proceso, cliente, dap, conf);
			}
		} 
		else {
			respuesta = respuestaContrato( contrato,"N",  "00003 SETEANDO VALOR",  "----");
		}

		//intentando borrar archivos
		String nombreDocumento = contrato.getNombreOriginal();
		borrarArchivo(conf.getParamCMO(URL_DIR_COPIA)+"COPIA_"+nombreDocumento);
		borrarArchivo(conf.getParamCMO(URL_DIR_FIRMA)+"FIRMADO_"+nombreDocumento);
		borrarArchivo(conf.getParamCMO(URL_DIR_ORIGI)+nombreDocumento);					
		log.info("{} CrearTodo -> makeContract -> Enviando respuesta:{} ",hilo, respuesta);
		return respuesta;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public void borrarArchivo(String ruta){
		try{
			File f = new File(ruta);
			if(f.exists()){
				eliminarFiles(f, "");
			}
		}catch(Exception ex){
			log.error("El archivo {} no se pudo eliminar: {}",ruta ,ex.getMessage());
		}
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String respuestaContrato(ContratoDTO contrato,String flag ,String seguridad, String cmoCode){
		String resp = "";
		
		resp += Utils.rellenarEspacios(contrato.getCodigoContrato() ,2 );
		resp += Utils.rellenarEspacios(flag ,1 );
		resp += Utils.rellenarEspacios(seguridad, 20 );
		resp += Utils.rellenarEspacios(cmoCode ,15 );

		return resp;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public static String sumarAnios(String fecha, int cantidad, String hilo) {
		String resp = "";
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			Date date = formatter.parse(fecha);
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			calendar.add(Calendar.YEAR, cantidad);
			SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			resp = formatter2.format(calendar.getTime()); 
			log.error("{} Fecha convertida: {}", hilo, resp);
		} catch (ParseException e) {
			
			log.error("{} Error convirtiendo la fecha", hilo);
			resp = fecha;
		} catch (Exception gral) {
			gral.printStackTrace();
			log.error("{} Error convirtiendo la fecha", hilo);
			resp = fecha;
		}
		return resp;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public static String fechaEnTexto(String timeStamp ,String hilo){
		String resp = "";
		try {
			String []meses 				= {"Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciemre"};
			int    indice				= Integer.parseInt(timeStamp.substring(4,5)) - 1;
			String dia 					= timeStamp.substring(0,2);
			String mes 					= ""+meses[indice];
			String anio					= timeStamp.substring(6,10);
			resp 						= dia + " de " + mes + " del " + anio;
			log.info("{} Se convirtiò la fecha" , hilo);
			 
		} catch (Exception e) {
			log.error("{} Error convirtiendo la fecha a texto completo", hilo);
			resp = timeStamp;
		}
		
		return resp;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public static String crearb64CopiaDeclaracion(ContratoDTO contrato, String hilo){
		
		String base64 = "";
		try {
			String url = contrato.getUrlCopy()+contrato.getNombreCopia();
			log.info("{} Url : {}" , hilo, url );
			File file = new File(url);
			base64 = Utils.crearImagenPDF(file);
			if ( base64 != null && base64.length() > 0 ){
				log.info("{} Se realizó conversion con exito", hilo );
				
			}
			else{
				log.info("{} No se pudo convertir a b64" , hilo);
				base64 = "";
			}
			
		} catch (Exception e) {
			base64 = "";
			log.error("{} Error creando b64 copia dec jurada" , hilo);
		}
		return base64;
	}
	
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public static boolean unirArchivos(File f1, File f2 ,String destino, String hilo) {
		boolean resp = false;
		try {
			List<InputStream> inputPdfList = new ArrayList<>();
			inputPdfList.add(new FileInputStream(f1));
			inputPdfList.add(new FileInputStream(f2));
			FileOutputStream outputStream = new FileOutputStream(destino);
			PDFMerger.mergePdfFiles(inputPdfList, outputStream);
			resp = true;
			
			log.info("{} Se unieron los archivos", hilo);
		} catch (Exception e) {
			resp = false;
			log.error("{} Error uniendo los archivos", hilo);
		}
		return resp;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String envioCorreosNotificaciones(ProcesoDTO proceso){
		String hilo 					= proceso.getHilo();
		String respuesta 				= "";
		
		SimpleDateFormat sdf1 			= new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat sdf2 			= new SimpleDateFormat("ddMMyyyy");
		Date fechaTmp 					= null;
		try {
			
			String documentosEmail 	= "";
			String[] params 		= proceso.getParams();
			log.info("{} Long Params :{}", hilo , params.length );
			DapDTO dap 				= new DapDTO();
			ClienteDTO cliente  	= new ClienteDTO();
			EnviarLatinia correo 	= new EnviarLatinia();
						
			String tipo				= params[1].trim();
			String timestamp		= params[2].trim();
			String numeroUnico		= params[3].trim();
			String nombreCliente	= params[4].trim();
			String email			= params[5].trim().toLowerCase();
			String numDap			= params[6].trim();
			String vencDap			= params[7].trim();
			String referencia 		= "";
			String plantilla 		= "";
			

			
			
			if( tipo.equalsIgnoreCase("1") ){
				referencia = "refDepositoPlazo";
				plantilla = "plaDAPVencimiento";
				
				cliente.setNombreCliente(nombreCliente);
				dap.setNumeroDAP(		 numDap);
				
				fechaTmp = sdf1.parse(params[7].trim());
				String nueva = sdf2.format(fechaTmp);
				dap.setVencimiento(	nueva );
			}
			else if( tipo.equalsIgnoreCase("2") ){
				referencia = CONTRATO_ACT_DATOS;
				plantilla 	= "plaActualizaDatosIngresos";
				
			}
			
			else if( tipo.equalsIgnoreCase("3") ){
				referencia 	= CONTRATO_ACT_DATOS;
				plantilla = "plaActualizaDatosFatca";
				
				documentosEmail =	"<Documento><nombreDocumento>FormW9.pdf</nombreDocumento></Documento><Documento><nombreDocumento>CartaWaiver.pdf</nombreDocumento></Documento>";				 
			}
			else if( tipo.equalsIgnoreCase("4") ){
				referencia 			= CONTRATO_ACT_DATOS;
				plantilla 			= "plaActualizaDatosPep";
				documentosEmail 	= "<Documento><nombreDocumento>FormPEP.pdf</nombreDocumento></Documento>";
			}
			
			else if( tipo.equalsIgnoreCase("5") ){		
				referencia 			= CONTRATO_ACT_DATOS;
				plantilla 			= "plaActualizaDatosPepRel";
				documentosEmail 	= "<Documento><nombreDocumento>FormPepRel.pdf</nombreDocumento></Documento>";
			}
			
			proceso.setContratoCorreo(referencia);
			proceso.setContratoPlantilla(plantilla);
			cliente.setEmailCliente(email);
			cliente.setNombreCliente(nombreCliente);
			
			log.info("{} Tipo           :{}", hilo ,tipo );
			log.info("{} Timestamp      :{}", hilo , timestamp );
			log.info("{} NumeroUnico    :{}", hilo , numeroUnico );
			log.info("{} NombreCliente  :{}", hilo , nombreCliente );
			log.info("{} Correo         :{}", hilo ,  email );
			log.info("{} NumDap         :{}", hilo ,  numDap );
			log.info("{} VencDap        :{}", hilo , vencDap );
			
			String param = PlantillaLatinia.parametrosPlantilla( proceso.getContratoPlantilla() , cliente ,  dap);
			log.info("{} Parametros [{}]" , hilo , param );
			
			boolean correoEnviado = correo.enviarCorreoTodos(proceso, cliente.getEmailCliente(), param, documentosEmail );

			if(correoEnviado){
				respuesta = Procesar.respuestaGenerica(CODIGO0, FINALEXITO);
			}
			else  {
				respuesta =  Procesar.respuestaGenerica(CODIGO1, "ERROR AL ENVIAR EL CORREO, VERIFIQUE.");
			}
		} catch (Exception e) {
			respuesta =  Procesar.respuestaGenerica(CODIGO2, "ERROR AL ENVIAR EL CORREO, VERIFIQUE."); 
			log.error("{} Error general en notificaciones", hilo);
			
		}
		return respuesta;
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public String soloEnvioImg(ProcesoDTO proceso){
		
		ClienteDTO cliente = new ClienteDTO();
		EnviarMobbScan envio = new EnviarMobbScan();
		Date fecha	 			= new Date();
		SimpleDateFormat sdf 	= new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		String fecha2 			= sdf.format(fecha);
		
		String hilo 		= proceso.getHilo();
		String []params		= proceso.getParams();
		
		String numCliente 	= params[1].trim();
		String dui 			= params[2].trim();
		String vencimiento 	= params[3].trim();
		
		String idScan 		= params[4].trim();
		String idNit 		= params[5].trim();

		
		cliente.setNumCliente(numCliente);
		cliente.setDuiCliente(dui);
		cliente.setFechaVencDoc(vencimiento);
		cliente.setFechaVencDui(vencimiento);
		cliente.setIdScan(idScan);
		cliente.setIdScanNit(idNit);
		
		String contentImgs = envio.enviarImagenes( cliente ,  hilo);
		
		return "[" + fecha2 + "]["+ numCliente +"]["+ contentImgs +"]";
	}
	/*----------------------------------------------------------------------------------------------------------------------------------------------*/
	public File dclUnificado(ContratoDTO contrato , Map<?, ?> reportParams , ClienteDTO cliente , FullDecJurada dcl , String hilo , String urljasper){
		File contratoFile 			= null;
		File contratoFile2 			= null;
		File fusionado 				= null;
			contratoFile 	= DeclaracionJurada.generarPFDDeclaracionJurada(reportParams, cliente.getNumCliente(),	 urljasper, hilo);
			contratoFile2 	= DocVinculacion.docVinculacion(dcl,hilo);
			
			if (contratoFile2 != null && contratoFile != null && contratoFile.exists() && contratoFile2.exists()) {
				String destino = contrato.getUrlOriginal()	+ "Declaración_" + cliente.getNumCliente()+".pdf";
				unirArchivos(contratoFile, contratoFile2, destino, hilo);
				fusionado = new File(destino);
				contrato.setNombreOriginal("Declaración" + cliente.getNumCliente()+".pdf");
				contrato.setCreado(true);
				eliminarFiles(contratoFile, hilo);
				eliminarFiles(contratoFile2, hilo);
			}
		return fusionado;
	}
	
	
	public void eliminarFiles(File f, String hilo) {	
		if( f == null ) {
			log.error("{} El file es null", hilo);
		}
		else {
			Path p = f.toPath();
			try {
				Files.delete(p);
				log.info("{} Se elimino el archivo : {}", hilo , p);
			} catch (IOException e) {
				log.error("{} IOException : {}", hilo , e.getMessage());
			}
		}
		
		
		
	}
	
	public String noAlmacenaContent(File contratoFile, ContratoDTO contrato, ProcesoDTO proceso, ClienteDTO cliente) {
		String respuesta = "";
		String hilo = proceso.getHilo();
		boolean archivosCreados	= false;
	
		Map<Integer, String> docAhorro = Utils.nombreDocumentos();
		log.info("{} MDB -> CREAR PLANTILLAS F2F -> NO SE ALMACENAN EN CM" , hilo);
		contrato.setFirmarDoc("N");
		contrato.setAlmacenarCM("N");
        int codigo = contrato.getNumContrato();
		
		if (contratoFile != null && contratoFile.exists() && contrato.isCreado()) {
			log.info("{} MDB -> CREAR_CONTRATO -> CREANDO ARCHIVOS [ORIGINAL , COPIA , FIRMA ] ", hilo);
			archivosCreados = Procesar.crearArchivos(contratoFile, contrato, hilo);
			
			if (archivosCreados && contrato.isCopiado()) {
			    
			    String doc = contrato.getNombreOriginal();
			    
			 // para la manipulación de archivos
		        ManejoArchivo original = new ManejoArchivo( contrato.getUrlOriginal() + doc);
		        ManejoArchivo copia = new ManejoArchivo( contrato.getUrlCopy() + doc);
		        ManejoArchivo declaracion = new ManejoArchivo(contrato.getUrlOriginal() + "Declaracion_" + cliente.getNumCliente() + ".pdf");

                String nombreArchivo = cliente.getNumeroUnico()  + docAhorro.get(codigo) + ".pdf";
                
                Path destino = Paths.get(original.obtenerRuta());
                destino = destino.normalize().getParent();
                destino = destino.resolve(cliente.getNumeroUnico() + "/" + nombreArchivo);

                try {
                    original.copiarArchivo(destino.toString());
                    
                    original.eliminarArchivo();
                    copia.eliminarArchivo();
                    declaracion.eliminarArchivo();
                } catch (Exception e) {
                    log.error("{} MDB -> CREAR_CONTRATO -> No fue posible copiar el archivo a carpeta temporal{} ", hilo , e.getMessage());
                }

                documentosComprimir.add(nombreArchivo);
                
                respuestaContrato(contrato, "Y", "000000 ERROR CREANDO", " ARCHIVO");
			} else {
				respuesta = respuestaContrato( contrato,"N",  "00005 CERTIF Y FIRMA",  "----");
			}
		} else {
			respuesta = respuestaContrato( contrato,"N",  "00004 CREANDO PDF",  "----");
			
		}
		return respuesta;
	}
	
	
	public String siAlmacenaContent(File contratoFile, ContratoDTO contrato, ProcesoDTO proceso, ClienteDTO cliente, DapDTO dap, Configuracion conf) {
		String respuesta = "";
		String hilo = proceso.getHilo();
		boolean archivosCreados	= false;
		boolean sendCopyCMO	= false;
		boolean sendOrigCMO	= false;
		

		String urlCopia 		= conf.getParamCMO(URL_DIR_COPIA); 
		String urlFirmas  		= conf.getParamCMO(URL_DIR_FIRMA);
		
		ContentManagerOracle cmo= new ContentManagerOracle();
		if (contrato.isCreado()) {
			log.info("{} MDB -> CREAR_CONTRATO -> CREANDO ARCHIVOS [ORIGINAL , COPIA , FIRMA ] ",hilo);
			archivosCreados = Procesar.crearArchivos(contratoFile,	contrato, hilo);
			eliminarFiles(contratoFile, hilo);
			
			if (archivosCreados) {
				
				if (proceso.getCodigoContrato().equals("40")){
				    	cmo.envioF2F(proceso.getParams()[57].toUpperCase().contains("F2F"));
					}
				
				if(  contrato.getCodigoContrato().equals("52") ){
						log.info("{} MDB -> CREAR_CONTRATO -> ANTES DE SUBIR AL CMO -> CUENTA = DAP CANCELADO  ",hilo);
						cliente.setNumCuenta( dap.getNumeroDAP() );
					}
				
				log.info("{} MDB -> CREAR_CONTRATO -> SUBIENDO COPIA AL CMO  ",hilo);
				sendCopyCMO = cmo.enviarContentOra(urlCopia, contrato,	cliente, true, hilo);
				log.info("-------------------------->{}",sendCopyCMO);
				if (sendCopyCMO) {
					log.info("{} MDB -> CREAR_CONTRATO -> SUBIENDO ORIGINAL AL CMO  ",hilo);
					sendOrigCMO = cmo.enviarContentOra(urlFirmas,	contrato, cliente, false, hilo);
					
					respuesta = metodoRespueta(sendOrigCMO, contrato, hilo, archivosCreados);

				} else {

					respuesta = respuestaContrato( contrato,"N",  "00006 CREA CMO COPIA",  "----");
				}
			} else {
				respuesta = respuestaContrato( contrato,"N",  "00005 CERTIF Y FIRMA",  "----");
			}
		} 
		else {
			respuesta = respuestaContrato( contrato,"N",  "00004 CREANDO PDF",  "----");
			
		}
		return respuesta;
	}
	
	/*------------------------------------------------------------------------------------------------------------------------------------------*/
	public boolean validarTramas(ProcesoDTO proceso) {
		Validacion validar = new Validacion();
		boolean validado = false;
		String hilo = proceso.getHilo();
		switch (proceso.getCodigoContNum()) {			
			case 20:    validado = validar.validarTramaCancelacion(proceso, hilo);  break;
			case 30:	validado = validar.validarTramaDeclaracion(proceso,hilo);	break;
			case 40:	validado = validar.validarTramaCta0254(proceso,hilo);		break;
			case 50:	validado = validar.validarTramaDAP(proceso,hilo);			break;
			default :	validado = false;break;
		}
		return validado;
	}
	
	/*------------------------------------------------------------------------------------------------------------------------------------------*/
	public boolean isF2FAndTemplates(ProcesoDTO proceso, ClienteDTO cliente) {
        boolean isF2F = false;
        boolean isTemplates = false;
		if (proceso.getCodigoContNum() == 40
		    && proceso.getParams()[57].toUpperCase().contains("F2F")) {
		    isF2F = true;
		}
        if (!(cliente.getCodigoOTP() == null
            || cliente.getCodigoOTP().isEmpty())) {
            isTemplates = true;
        }
        return isF2F && isTemplates;
	}
	
	
	/*------------------------------------------------------------------------------------------------------------------------------------------*/
	public Map<String,Object> generarCarpetaTemporalF2F(ProcesoDTO proceso, ClienteDTO cliente){
		Map<String,Object>r = new HashMap<>(); 
		boolean exito 		= false;
		String hilo 		= proceso.getHilo();
		Configuracion conf 	= new Configuracion();
		Configuracion.cargarPropertiesCMO();
        
	    if (isF2FAndTemplates(proceso, cliente)) {
	        Path rutaZip = null;
	        Path rutaArchivos = null;
	        documentosComprimir = new ArrayList<>();    
	        rutaZip = Paths.get(conf.getParamCMO(URL_DIR_ORIGI));
	        rutaZip = rutaZip.normalize().toAbsolutePath();
	        rutaArchivos = rutaZip.resolve(String.valueOf(cliente.getNumeroUnico()));
	        log.info("{} Intentando generar carpeta temporal de cliente plantillas F2F", hilo);
	        try {
	            Files.createDirectories(rutaArchivos);
	            rutaZip = rutaZip.resolve(cliente.getNumeroUnico() + ".zip");
	            log.info("{} Carpeta temporal generada con éxito {}",hilo, rutaZip );
	            r.put("rutaZip", rutaZip);
	            r.put("rutaArchivos", rutaArchivos);
	            exito = true;
	        } catch (Exception e) {
	            log.error("{} Error al intentar crear carpeta temporal de cliente : {}", hilo  , e.getMessage());
	            exito = false;
	        }
	        r.put("exito", exito);
	    }
	    return r;
	}
	
	/*------------------------------------------------------------------------------------------------------------------------------------------*/
	public Map<String, String> generandoContratos(String[] cntLst,  Map<String, Object> p) {
		Map<String , String> resp = new HashMap<>();
		
		ContratoDTO contrato 	= (ContratoDTO) 	p.get(CONTRATO);
		String hilo 			= (String) 			p.get("hilo");
		ClienteDTO cliente 		= (ClienteDTO) 		p.get(CLIENTE);
		EnviarMobbScan escaneo  = (EnviarMobbScan)  p.get("escaneo");
		
		StringBuilder bld1 		= new StringBuilder();
		StringBuilder bld2 		= new StringBuilder();
		
		for (int i = 0; i < cntLst.length; i++) {
			String contName = cntLst[i].trim();
			if( contName.length() > 0 ){
				log.info("{} CREANDO CONTRATO [{}]" , hilo  , cntLst[i]);
				contrato.setCodigoContrato(""+cntLst[i]);
				if(contrato.getNumContrato() >= 1 && contrato.getNumContrato() <= 59){
					
					String resp1 		= makeContract(p );
					bld1.append(resp1);
											
					
					bld2 = metodoNuevo(resp1, hilo, contrato);
					
				}else if( contrato.getNumContrato() >= 60 && contrato.getNumContrato() <= 69){
					String cmoCodes 		= escaneo.enviarImagenes(cliente, hilo);
					bld1.append(cmoCodes);
				}
			
			}
			
		}
		
		resp.put("respuesta", bld1.toString());
		resp.put("documentos", bld2.toString());
		return resp;
	}
	/*------------------------------------------------------------------------------------------------------------------------------------------*/
	
	public String metodo2(Map<String, Object> f2f, ProcesoDTO proceso,ClienteDTO cliente,String hilo,String detalleContratos) {
		Configuracion conf = new Configuracion();
		Configuracion.cargarPropertiesCMO();
		String respuesta = "";
		
		
		if(isF2FAndTemplates(proceso, cliente)){
			Path rutaArchivos 	= (Path) f2f.get("rutaArchivos");
			Path rutaZip		=  (Path) f2f.get("rutaZip");
			
            if (ExportarContrato.comprimirArchivos(hilo, rutaZip, rutaArchivos)) {
                rutaArchivos = rutaArchivos.resolve(rutaZip.getFileName()).normalize();
                try {
                    Files.move(rutaZip, rutaArchivos, StandardCopyOption.REPLACE_EXISTING);
                } 
                catch (IOException e) {
                    log.error("{} No fue posible mover el zip a carpeta temporal {}", hilo ,e.getMessage());
                }
                rutaArchivos = rutaArchivos.getParent();
                rutaZip = rutaZip.getParent().resolve( String.valueOf(cliente.getNumeroUnico()));
                rutaZip = rutaZip.resolve( String.valueOf(cliente.getNumeroUnico()) + ".zip").normalize();
                String nombreRemoto = String.valueOf(cliente.getNumeroUnico())+ "_ContratosAhorro.zip";
                if (!ExportarContrato.publicarZipSftp(hilo, conf, rutaZip.toString(), nombreRemoto)) {
                    respuesta = Procesar.respuestaErrorTodos(CODIGO4,NOCONTRATOS+ detalleContratos.length());
                } else {
                    respuesta = Procesar.respuestaExitoTdodos( CODIGO0, FINALEXITO, cliente.getNumCuenta(), cliente.getNumCliente(), "", cliente.getFecha(), detalleContratos);
                }
                try {
                    Files.walkFileTree(rutaArchivos, new DeleteDirVisitor());
                } catch (IOException | SecurityException e) {
                	String ruta = rutaZip.toString();
                    log.error("{} No fue posible eliminar el directorio temporal del cliente {} : {}",hilo ,ruta ,e.getMessage());
                }
            } else {
                respuesta = Procesar.respuestaErrorTodos(CODIGO4,NOCONTRATOS  + detalleContratos.length());
            }
		}
		else {
			respuesta = Procesar.respuestaErrorTodos(CODIGO4,NOCONTRATOS  + detalleContratos.length());
		}
		return respuesta;
	}
	
	
	public String metodo1(String detalleContratos, String documentosEmail, ProcesoDTO proceso, ClienteDTO cliente, DapDTO dap) {
		String respuesta = "";
		EnviarLatinia correo 		= new EnviarLatinia();
		String params = PlantillaLatinia.parametrosPlantilla(proceso.getContratoPlantilla(),cliente , dap);
		
		correo.envioF2F(proceso.getCodigoContNum() == 40 && proceso.getParams()[57].toUpperCase().contains("F2F"));
		
		boolean correoEnviado = correo.enviarCorreoTodos(proceso, cliente.getEmailCliente(), params,documentosEmail);
		if (correoEnviado) {
			respuesta = Procesar.respuestaExitoTdodos(CODIGO0,FINALEXITO , cliente.getNumCuenta(), cliente.getNumCliente(), "", cliente.getFecha(),	detalleContratos);
		} else {
			respuesta = Procesar.respuestaErrorTodos(CODIGO3, INIVALORES );
		}
		return respuesta;
	}
	
	public void mostrarResumen(ClienteDTO cliente, ContratoDTO contrato, String hilo, String respuesta) {
		if(cliente.getFirmado().equals("Y")){

			log.info( "{} ------------ DATOS -------------------------",hilo);
			log.info( "{} CODIGO                  :{}" ,hilo, contrato.getCodigoContrato() );
			log.info( "{} NUMCLIENTE1             :{}" ,hilo ,cliente.getNumeroUnico() );
			log.info( "{} NUMCLIENTE2             :{}" ,hilo , cliente.getNumCliente()  );
			log.info( "{} NUMCUENTA               :{}" ,hilo , cliente.getNumeroCuenta() );
			log.info( "{} NUMCUENTA2              :{}" ,hilo , cliente.getNumCuenta() );
			log.info( "{} EMAIL                   :{}" ,hilo , cliente.getEmailCliente() );
			log.info( "{} ------------ PROCESO -------------------------",hilo );
			log.info( "{} DATOS CORRECTOS         :{}" ,hilo , contrato.isValidado() );
			log.info( "{} CODIGO SEGURIDAD        :{}" ,hilo , contrato.getSeguridad());
			log.info( "{} NOMBRE CONTRATO ORIGINAL:{}" ,hilo , contrato.getNombreOriginal());
			log.info( "{} NOMBRE CONTRATO COPIA   :{}" ,hilo , contrato.getNombreCopia());
			log.info( "{} NOMBRE CONTRATO FIRMA   :{}" ,hilo , contrato.getNombreFirmado());
			log.info( "{} X TXT CODDOCUMENTO      :{}" ,hilo , contrato.getCodigoDocu());
			log.info( "{} D SECURITY GROUP        :{}" ,hilo , contrato.getEntidad());
			log.info( "{} D SECURITY GROUP ORG    :{}" ,hilo , contrato.getEntidadOrg());
			log.info( "{} X FECH DOCUMENT         :{}" ,hilo , cliente.getFechaCMO());
			log.info( "{} ENVIO CONTRATO COPIA    :{}" ,hilo , contrato.getCodigoCMO());
			log.info( "{} ENVIO CONTRATO FIRMA    :{}" ,hilo , contrato.getCodigoCMOOrg());
			log.info( "{} ------------ RESPUESTA -------------------------", hilo);
			log.info( "{}" , respuesta );

		}
	}

	
	public StringBuilder metodoNuevo(String resp1, String hilo , ContratoDTO contrato  ) {
		
		StringBuilder bld2 = new StringBuilder(); 
		if( resp1.trim().length() >= 3){
			if( resp1.substring(2,3).equalsIgnoreCase("Y") ){
				log.info("{} Contrato: {}",hilo  ,contrato.getNombreOriginal());
						
				String tmp =  "<documento>"+
											"<nombreDocumento>"+contrato.getNombreOriginal()+"</nombreDocumento>"+
											"<base64>"+contrato.getImgB64Copy()+"</base64>"+
										"</documento>";
				bld2.append(tmp);
				
			}
			
			String nombreDoc = contrato.getNombreOriginal();
			int base64 = contrato.getImgB64Copy().length();
			
			log.info("{} Archivo : {} Longitud : {} ", hilo , nombreDoc, base64);
			
		}
		return bld2;
	}
	
	public String metodoRespueta(boolean sendOrigCMO, ContratoDTO contrato, String hilo, boolean archivosCreados) {
		String respuesta = "";
		if (sendOrigCMO) {
			log.info("{} MDB -> CREAR_CONTRATO -> ENVIANDO COPIA AL CLIENTE  ",hilo);
			
			if(contrato.getNumContrato() == 46){
				Utils.cortarDCL(contrato);
			}
			
			respuesta = respuestaContrato( contrato,"Y", contrato.getSeguridad(),contrato.getCodigoCMO()+"-"+contrato.getCodigoCMOOrg()  );
			log.info("{} MDB -> RESUMEN CONTRATO ", hilo);
			log.info("{} MDB -> ...VALIDADO           -> true", hilo);
			log.info("{} MDB -> ...INICIALIZADO       -> true", hilo);
			log.info("{} MDB -> ...EXISTE             -> {}"  ,hilo , hilo);
			log.info("{} MDB -> ...CREADO             -> {}"  ,hilo , archivosCreados );
			log.info("{} MDB -> ...COPIADO            -> {}"  ,hilo , contrato.isCopiado());
			log.info("{} MDB -> ...FIRMADO            -> {}"  ,hilo , contrato.isFirmado() );
			log.info("{} MDB -> ...ALMACENADO COPIA   -> {}"  ,hilo , contrato.getCodigoCMO() );
			log.info("{} MDB -> ...ALMACENADO ORIG    -> {}"  ,hilo , contrato.getCodigoCMOOrg() );
			log.info("{} MDB -> ...EMAIL              -> {}"  ,hilo , respuesta );
			
			
		} else {
			respuesta = respuestaContrato( contrato,"N",  "00007 CREA CMO ORIGN",  "----");
		}
		return respuesta;
	}
	
	public String metodoRespuestaDCL(ContratoDTO contrato,ProcesoDTO proceso, ClienteDTO cliente, ContratoDTO contrato2, String urlCopia, File f) {
		String respuesta = "";
		EnviarLatinia latiniaSrv 	= new EnviarLatinia();
		boolean sendEmail = false;
		String hilo = proceso.getHilo();
		log.info("{} MDB -> DCL -> CREAR_CONTRATO -> CORTANDO DCL ", hilo);
		Utils.cortarPDF(contrato, hilo);
		File file = new File(contrato.getUrlCopy() + contrato.getNombreCopia());
		contrato2.setImgB64Copy(Utils.crearImagenPDF(file));
		
		latiniaSrv.envioF2F(proceso.getParams()[69].toUpperCase().contains("F2F"));
		
		log.info("{} MDB -> DCL -> CREAR_CONTRATO -> ENVIANDO COPIA AL CLIENTE",  hilo);
		sendEmail = latiniaSrv.enviarCorreo( cliente, contrato2, hilo);
		if (sendEmail && contrato2.isEnviadoCorreo()) {
			
			String []resp = {CODIGO0,
					FINALEXITO,
					contrato.getSeguridad(),
					cliente.getNumCuenta(),
					cliente.getNumCliente(),
					contrato.getCodProdTipoProd(),
					cliente.getFechaHora(),
					contrato.getCodigoCMO(),
					urlCopia,
					contrato.getNombreCopia()};
			
			respuesta = Procesar.respuestaExitosa(resp);
			
			/* aqui va la parte para eliminar archivos  */
			File f1 = new File(contrato.getUrlFirma() + contrato.getNombreFirmado());
			File f2 = new File(contrato.getUrlFirma() + contrato.getNombreOriginal());
			eliminarFiles(f, hilo);
			eliminarFiles(file, hilo);
			eliminarFiles(f1, hilo);
			eliminarFiles(f2, hilo);
			
		}
		else {
			respuesta = Procesar.respuestaError("000009","Error enviando correo");
		}
		return respuesta;
	}

	
	public String crearContrato(ProcesoDTO proceso, ClienteDTO cliente, ContratoDTO contrato,Map<?, ?> reportParams, FullDecJurada declaracion, ContratoDTO contrato2) {
		String respuesta = "";
		File contratoFile 			= null;
		File contratoFile2 			= null;		

		boolean creados1 			= false;	
		String hilo 				= proceso.getHilo();
		Configuracion conf 			= new Configuracion();
		String urljasper 			= conf.getParamCMO(URL_DIR_JASPER); 	

		contratoFile 	= DeclaracionJurada.generarPFDDeclaracionJurada(reportParams, cliente.getNumCliente(),	 urljasper, hilo);
		contratoFile2 	= DocVinculacion.docVinculacion(declaracion,hilo);					
		
		log.info("{} MDB -> DCL -> SE CREO DJ Y FORM VINC" , hilo);
		if (contratoFile2 != null && contratoFile != null && contratoFile.exists() && contratoFile2.exists()) {
			
			log.info("{} MDB -> DCL -> UNIENDO ARCHIVOS y CREANDO COPIAS", hilo);
			String destino = contrato.getUrlOriginal()	+ "DCL_" + cliente.getNumCliente()+".pdf";
			boolean unidos = unirArchivos(contratoFile, contratoFile2, destino, hilo);
		
			
			if( unidos ){
				log.info("{} MDB -> DCL -> SE HA UNIDO AMBOS PDF", hilo);
				eliminarFiles(contratoFile, hilo);
				eliminarFiles(contratoFile2, hilo);
				log.info("{} MDB -> DCL -> SE BORRARON LOS ARCHIVOS INDEPENDIENTES" , hilo);
				File f = new File(destino);
				creados1 = Procesar.crearArchivos(f,contrato, hilo);
				if ( creados1 ) {
					respuesta = creados(proceso, contrato, contrato2, cliente, creados1, f);
				}
				else {
					respuesta = Procesar.respuestaError(CODIGO6,"Error creando base64 copia");
				}	
			}
			else{
				respuesta = Procesar.respuestaError(CODIGO5,"Error fusionando archivos");
			}
		} 
		else {
			respuesta = Procesar.respuestaError(CODIGO4,	"Error creando archivos");
		}
		return respuesta;
	}
	
	public String creados(ProcesoDTO proceso, ContratoDTO contrato , ContratoDTO contrato2 , ClienteDTO cliente, boolean creados1, File f) {
		String respuesta 			= "";
		Configuracion conf 			= new Configuracion();
		String urlCopia 			= conf.getParamCMO(URL_DIR_COPIA);
		String urlFirmas 			= conf.getParamCMO(URL_DIR_FIRMA);
		String hilo 				=proceso.getHilo();
		boolean sendOrigCMO 		= false;
		boolean sendCopyCMO 		= false;
		ContentManagerOracle cmo 	= new ContentManagerOracle();		
	    cmo.envioF2F(proceso.getParams()[69].toUpperCase().contains("F2F"));
		log.info("{} MDB -> DCL -> EXISTEN LOS 6 ARCHIVOS  : {}", hilo, creados1 );
		sendCopyCMO = cmo.enviarContentOra(urlCopia,	contrato, cliente, true, hilo);
		if (sendCopyCMO) {
			log.info("{} MDB -> DCL -> CREAR_CONTRATO -> SUBIENDO ORIGINAL AL CMO  ",hilo);
			sendOrigCMO = cmo.enviarContentOra(urlFirmas, contrato, cliente,false, hilo);
			
			if(sendOrigCMO ) {
				respuesta = metodoRespuestaDCL(contrato, proceso, cliente, contrato2, urlCopia, f);
			}
			else {
				respuesta = Procesar.respuestaError("000008","Error enviando original a cmo");
			}
		} 
		else {
			respuesta = Procesar.respuestaError(CODIGO7,"Error enviando copia a cmo");
		}
		return respuesta;
	}
	
}
