package com.pa.extractor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.pa.database.impl.DatabaseFacade;
import com.pa.entity.Curriculo;
import com.pa.entity.Orientation;
import com.pa.entity.OrientationType;
import com.pa.entity.Publication;
import com.pa.entity.PublicationType;
import com.pa.entity.TechinicalProduction;
import com.pa.exception.InvalidPatternFileException;
import com.pa.util.EnumPublicationLocalType;

public class XMLExtractor {

	public Curriculo lattesExtractor(InputStream file) throws InvalidPatternFileException {
		Curriculo curriculo = null;

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();

			// Load and Parse the XML document
			Document document = builder.parse(file);

			curriculo = this.extractBasicInformations(document);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			throw new InvalidPatternFileException(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			throw new InvalidPatternFileException(e.getMessage());
		}

		if (curriculo == null) {
			throw new InvalidPatternFileException("XML file is invalid: the file is not about a lattes curriculum");
		}

		return curriculo;
	}

	private Curriculo extractBasicInformations(Document document) {
		Curriculo curriculo = null;
		String name;
		Long id = null;
		Date lastUpdate = null;

		if (document.getDocumentElement().getNodeName().equals("CURRICULO-VITAE")) {
			// Get last update from xml
			String update = document.getDocumentElement().getAttributes().getNamedItem("DATA-ATUALIZACAO")
					.getNodeValue();
			try {
				SimpleDateFormat sdf1 = new SimpleDateFormat("ddMMyyyy");
				lastUpdate = sdf1.parse(update);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			// Get identifier
			String identifier = document.getDocumentElement().getAttributes().getNamedItem("NUMERO-IDENTIFICADOR")
					.getNodeValue();
			id = Long.valueOf(identifier);

			// Iterating through the nodes and extracting the data.
			NodeList nodeList = document.getDocumentElement().getChildNodes();

			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);

				if (node instanceof Element) {
					if (node.getNodeName().equals("DADOS-GERAIS")) {
						// Get name
						name = node.getAttributes().getNamedItem("NOME-COMPLETO").getNodeValue();

						if (id != null && !name.isEmpty() && lastUpdate != null) {
							curriculo = new Curriculo(name, lastUpdate);
							curriculo.setId(id);
						}
					} else if (node.getNodeName().equals("PRODUCAO-BIBLIOGRAFICA")) {
						// Publica��es
						curriculo.setPublications(this.extractPublications(node));
					} else if (node.getNodeName().equals("PRODUCAO-TECNICA")) {
						// Software
						curriculo.setTechinicalProduction(this.extractTechinicalProduction(node));
					} else if (node.getNodeName().equals("OUTRA-PRODUCAO")) {
						// Orienta��es conclu�das
						int orientations = this.extractQtdOrientations(node, "ORIENTACOES-CONCLUIDAS-PARA-MESTRADO");
						orientations += this.extractQtdOrientations(node, "ORIENTACOES-CONCLUIDAS-PARA-DOUTORADO");
						curriculo.setCountConcludedOrientations(orientations);
						curriculo.setConcludedOriantations(this.extractOrientations(node));
					} else if (node.getNodeName().equals("DADOS-COMPLEMENTARES")) {
						// Orienta��es n�o conclu�das
						int orientations = this.extractQtdOrientations(node, "ORIENTACAO-EM-ANDAMENTO-DE-MESTRADO");
						orientations += this.extractQtdOrientations(node, "ORIENTACAO-EM-ANDAMENTO-DE-DOUTORADO");
						curriculo.setCountOnGoingOrientations(orientations);
						curriculo.setOnGoingOriantations(this.extractOrientations(node));
					}
				}
			}
		}

		return curriculo;
	}

	private ArrayList<TechinicalProduction> extractTechinicalProduction(Node nodeProduction) {
		ArrayList<TechinicalProduction> techinicalProductions = new ArrayList<TechinicalProduction>();

		NodeList nodeList = nodeProduction.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);

			if (node instanceof Element) {
				// System.out.println(node.getNodeName());
				if (node.getNodeName().equals("SOFTWARE")) {
					NodeList events = node.getChildNodes();

					Node basicDataEvent = events.item(0);
					if (basicDataEvent != null) {
						if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DO-SOFTWARE")) {
							Node softwareTitle = basicDataEvent.getAttributes().getNamedItem("TITULO-DO-SOFTWARE");
							Node softwareYear = basicDataEvent.getAttributes().getNamedItem("ANO");
							Node softwareLanguage = basicDataEvent.getAttributes().getNamedItem("IDIOMA");

							if (softwareTitle != null) {
								TechinicalProduction techinicalProduction = new TechinicalProduction(
										softwareTitle.getNodeValue(), softwareYear.getNodeValue(),
										softwareLanguage.getNodeValue());

								techinicalProductions.add(techinicalProduction);
							}
						}
					}
				}
			}
		}

		return techinicalProductions;
	}

	private ArrayList<Orientation> extractOrientations(Node dataNode) {
		ArrayList<Orientation> orientations = new ArrayList<Orientation>();

		NodeList nodeList = dataNode.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			// System.out.println(node.getNodeName());
			NodeList filhosDeIde2 = node.getChildNodes();
			for (int j = 0; j < filhosDeIde2.getLength(); j++) {
				Node node2 = filhosDeIde2.item(j);
				Node basicDataEvent = node2.getChildNodes().item(0);
				if (basicDataEvent != null) {
					// System.out.println(basicDataEvent.getNodeName());
					if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DE-ORIENTACOES-CONCLUIDAS-PARA-MESTRADO")) {
						extractBasicDataOrientations("TITULO", orientations, basicDataEvent,
								OrientationType.ORIENTACOES_CONCLUIDAS_PARA_MESTRADO);
					}
					if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DE-ORIENTACOES-CONCLUIDAS-PARA-DOUTORADO")) {
						extractBasicDataOrientations("TITULO", orientations, basicDataEvent,
								OrientationType.ORIENTACOES_CONCLUIDAS_PARA_DOUTORADO);
					}
					if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DA-ORIENTACAO-EM-ANDAMENTO-DE-MESTRADO")) {
						extractBasicDataOrientations("TITULO-DO-TRABALHO", orientations, basicDataEvent,
								OrientationType.ORIENTACAO_EM_ANDAMENTO_DE_MESTRADO);
					}
					if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DA-ORIENTACAO-EM-ANDAMENTO-DE-DOUTORADO")) {
						extractBasicDataOrientations("TITULO-DO-TRABALHO", orientations, basicDataEvent,
								OrientationType.ORIENTACAO_EM_ANDAMENTO_DE_DOUTORADO);
					}
				}
			}
		}
		return orientations;
	}

	private void extractBasicDataOrientations(String title, ArrayList<Orientation> orientations, Node basicDataEvent,
			OrientationType orientationType) {
		Node orientationTitle = basicDataEvent.getAttributes().getNamedItem(title);
		Node orientationNatureza = basicDataEvent.getAttributes().getNamedItem("NATUREZA");
		Node orientationYear = basicDataEvent.getAttributes().getNamedItem("ANO");
		Node orientationLanguage = basicDataEvent.getAttributes().getNamedItem("IDIOMA");

		if (orientationTitle != null) {
			Orientation orientation = new Orientation(orientationNatureza.getNodeValue(), orientationType,
					orientationTitle.getNodeValue(), orientationYear.getNodeValue(),
					orientationLanguage.getNodeValue());
			orientations.add(orientation);
		}
	}

	private Set<Publication> extractPublications(Node nodeProduction) {
		Set<Publication> publications = new HashSet<Publication>();

		NodeList nodeList = nodeProduction.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);

			if (node instanceof Element) {
				if (node.getNodeName().equals("TRABALHOS-EM-EVENTOS")) {
					NodeList events = node.getChildNodes();

					for (int j = 0; j < events.getLength(); j++) {
						// Evento (Confer�ncia)
						Node event = events.item(j);
						Node basicDataEvent = event.getChildNodes().item(0);
						if (basicDataEvent != null) {
							if (basicDataEvent.getNodeName().equals("DADOS-BASICOS-DO-TRABALHO")) {
								Node eventTitle = basicDataEvent.getAttributes().getNamedItem("TITULO-DO-TRABALHO");
								Node eventYear = basicDataEvent.getAttributes().getNamedItem("ANO-DO-TRABALHO");
								Node eventLanguage = basicDataEvent.getAttributes().getNamedItem("IDIOMA");

								if (eventTitle != null && eventYear != null) {
									PublicationType type = getPublicationType(event,
											EnumPublicationLocalType.CONFERENCE);
									Publication publication = new Publication(eventTitle.getNodeValue(),
											Integer.valueOf(eventYear.getNodeValue()), type);

									publication = getRealPublication(publication);

									if (publication.getId() == null) {
										publications.add(publication);
									}
								}
							}
						}
					}
				} else if (node.getNodeName().equals("ARTIGOS-PUBLICADOS")) {
					NodeList articles = node.getChildNodes();

					for (int j = 0; j < articles.getLength(); j++) {
						// Artigo (Periodico ou Revista)
						Node article = articles.item(j);
						Node basicDataArticle = article.getChildNodes().item(0);
						if (basicDataArticle != null) {
							if (basicDataArticle.getNodeName().equals("DADOS-BASICOS-DO-ARTIGO")) {
								Node articleTitle = basicDataArticle.getAttributes().getNamedItem("TITULO-DO-ARTIGO");
								Node articleYear = basicDataArticle.getAttributes().getNamedItem("ANO-DO-ARTIGO");
								Node eventLanguage = basicDataArticle.getAttributes().getNamedItem("IDIOMA");

								if (articleTitle != null && articleYear != null) {
									PublicationType type = getPublicationType(article,
											EnumPublicationLocalType.PERIODIC);
									Publication publication = new Publication(articleTitle.getNodeValue(),
											Integer.valueOf(articleYear.getNodeValue()), type);

									publication = getRealPublication(publication);
									
									if (publication.getId() == null) {
										publications.add(publication);
									}
								}
							}
						}
					}
				} else if (node.getNodeName().equals("LIVROS-E-CAPITULOS")) {
					ArrayList<TechinicalProduction> techinicalProductions = new ArrayList<TechinicalProduction>();

					NodeList nodeList1 = node.getChildNodes();
					for (int i1 = 0; i1 < nodeList1.getLength(); i1++) {
						Node node1 = nodeList1.item(i1);

						if (node1 instanceof Element) {
							if (node1.getNodeName().equals("LIVROS-PUBLICADOS-OU-ORGANIZADOS")) {
								NodeList events = node1.getChildNodes();

								Node basicDataEvent = events.item(0);
								if (basicDataEvent != null) {

									NodeList nodeList2 = basicDataEvent.getChildNodes();
									for (int i2 = 0; i2 < nodeList2.getLength(); i2++) {
										Node node2 = nodeList2.item(i2);

										if (node2 instanceof Element) {
											if (node2 != null) {
												if (node2.getNodeName().equals("DADOS-BASICOS-DO-LIVRO")) {
													Node softwareTitle = node2.getAttributes()
															.getNamedItem("TITULO-DO-LIVRO");
													Node softwareYear = node2.getAttributes().getNamedItem("ANO");
													Node softwareLanguage = node2.getAttributes()
															.getNamedItem("IDIOMA");

													if (softwareTitle != null) {
														Publication publication = new Publication(
																softwareTitle.getNodeValue(),
																Integer.valueOf(softwareYear.getNodeValue()), null);
														// publication =
														// getRealPublication(publication);

														publications.add(publication);
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return publications;
	}

	private Publication getRealPublication(Publication publication) {
		List<Publication> databasePublications = DatabaseFacade.getInstance().listAllPublications(publication);
		if (!databasePublications.isEmpty()) {
			for (Publication basePublication : databasePublications) {
				if (basePublication.getPublicationType().equals(publication.getPublicationType())
						&& basePublication.getTitle().equals(publication.getTitle())) {
					publication = basePublication;
				}
			}
		}
		return publication;
	}

	private PublicationType getPublicationType(Node mainNode, EnumPublicationLocalType local) {
		PublicationType type = null;
		String name;

		Node details = mainNode.getChildNodes().item(1);
		Node eventName = null;

		if (local.equals(EnumPublicationLocalType.CONFERENCE)) {
			if (details.getNodeName().equals("DETALHAMENTO-DO-TRABALHO")) {
				eventName = details.getAttributes().getNamedItem("NOME-DO-EVENTO");
			}
		} else if (local.equals(EnumPublicationLocalType.PERIODIC)) {
			if (details.getNodeName().equals("DETALHAMENTO-DO-ARTIGO")) {
				eventName = details.getAttributes().getNamedItem("TITULO-DO-PERIODICO-OU-REVISTA");
			}
		}

		if (eventName != null) {
			name = eventName.getNodeValue();
			type = new PublicationType(name, local);

			// Update objects if the publication already exists
			type = getRealPublicationType(type);
		}

		return type;
	}

	private PublicationType getRealPublicationType(PublicationType newType) {
		PublicationType databaseType = DatabaseFacade.getInstance().getPublicationTypeByNameAndType(newType.getName(),
				newType.getType());
		if (databaseType != null) {
			newType = databaseType;
		}

		return newType;
	}

	private int extractQtdOrientations(Node mainNode, String tag) {
		int orientations = 0;

		NodeList nodeList = mainNode.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);

			NodeList filhosDeIde2 = node.getChildNodes();
			for (int j = 0; j < filhosDeIde2.getLength(); j++) {
				Node node2 = filhosDeIde2.item(j);
				if (node2.getNodeName().equals(tag)) {
					orientations++;
				}
			}
		}
		return orientations;
	}
}
