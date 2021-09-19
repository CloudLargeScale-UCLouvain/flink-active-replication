package me.florianschmidt.examples.bettercloud;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import me.florianschmidt.examples.bettercloud.datagen.Cooperation;
import me.florianschmidt.examples.bettercloud.datagen.DocumentSharedEvent;
import me.florianschmidt.replication.baseline.FixedRateSource;

import java.util.Random;

class MockCustomerEventSource extends FixedRateSource<CustomerEvent> {

	private static final int NUM_CUSTOMERS = 250;

	public static final String[] TECH_COMPANIES = new String[]{
			"Amazon", "Netflix", "NVIDIA", "Salesforce", "ServiceNow",
			"Square", "AnalogDevices", "PaloAltoNetworks", "Splunk", "AdobeSystems",
			"Broadcom", "Leidos	Aerospace&Defense", "ONSemiconductorCorp.", "MatchGroup",
			"TechMahindra", "Workday", "CharterCommunications", "TencentHoldings",
			"MicronTechnology", "SKHynix", "Twitter", "AristaNetworks", "Baidu",
			"CatcherTechnology", "ConstellationSoftware", "Facebook", "Hikvision",
			"TokyoElectron", "ASMLHolding", "DassaultSystemes", "FocusInformationTechnology",
			"InfineonTechnologies", "Keyence", "Naver", "Unisplendour", "VMware", "Worldpay",
			"Apple", "CDW", "DellTechnologies", "Nokia", "Alibaba", "SunnyOpticalTechnologyGroup",
			"Infosys", "Intuit", "JD.com", "NEXON", "RedHat", "SamsungSDS",
			"AACTechnologiesHoldings", "Alphabet", "DiscoveryCommunications",
			"IPGPhotonics", "LamResearch", "VipshopHoldings", "Accenture", "Comcast",
			"Fortive", "Intel", "JabilCircuit", "SananOptoelectronics", "TaiwanSemiconductor",
			"TDK", "VeriSign", "Adyen", "Amphenol", "BOETechnologyGroup", "HCLTechnologies",
			"Microsoft", "Pinduoduo", "SpotifyTechnology", "WaltDisney", "Xiaomi", "Zalando",
			"AppliedMaterials", "MicrochipTechnology", "NanyaTechnology", "Sky", "TCLCorp",
			"ATOS", "MurataManufacturing", "Naspers", "NXP", "SAP", "STMicroelectronics",
			"AdvancedMicroDevices", "Cognizant", "DISHNetwork", "Inventec", "Tek", "NetApp",
			"NetEase", "Qualcomm", "SamsungElectronics", "SeagateTechnology", "SkyworksSolutions",
			"CheckPointSoftware", "CiscoSystems", "HewlettPackardEnterprise", "LARGANPrecision"
	};

	private long seed = 0;
	private Cooperation[] cooperations;
	private Random rnd;
	private ObjectMapper om;

	private Counter publicDocsCounter;
	private Counter privateDocsCounter;
	private Counter sharedDocsCounter;
	private Counter sharedButPrivateCounter;
	private Counter sharedAndAllowed;

	public MockCustomerEventSource(int rate, long seed) {
		super(rate);
		this.seed = seed;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);

		if (seed == 0) {
			this.rnd = new Random();
		} else {
			this.rnd = new Random(this.seed);
		}
		this.om = new ObjectMapper();
		MetricGroup metricGroup = getRuntimeContext().getMetricGroup();

		this.publicDocsCounter = metricGroup.counter("publicDocs");
		this.privateDocsCounter = metricGroup.counter("privateDocs");

		this.sharedDocsCounter = metricGroup.counter("sharedDocs");

		this.sharedButPrivateCounter = metricGroup.counter("sharedButShouldnt");
		this.sharedAndAllowed = metricGroup.counter("sharedAndAllowed");

		this.cooperations = new Cooperation[NUM_CUSTOMERS];

		for (int i = 0; i < NUM_CUSTOMERS; i++) {
			int numAvailableNames = TECH_COMPANIES.length;
			String name = TECH_COMPANIES[i % numAvailableNames];
			int suffix = Math.floorDiv(i, numAvailableNames);
			name = name + "-" + suffix;
			cooperations[i] = new Cooperation(name, 10_000);
		}
	}

	@Override
	public void doCollect(SourceContext<CustomerEvent> ctx) throws JsonProcessingException {
		Cooperation corp = cooperations[rnd.nextInt(cooperations.length)];


		boolean canBePublic = rnd.nextDouble() >= 0.95;
		boolean isShared;

		if (canBePublic) {
			isShared = rnd.nextDouble() >= 0.5;
		} else {
			isShared = rnd.nextDouble() >= 0.95;
		}

		DocumentSharedEvent event = randomDocumentSharedEvent(corp, isShared, canBePublic);
		CustomerEvent c = new CustomerEvent(System.currentTimeMillis(), corp.getName(), om.writeValueAsString(event), 0);
		ctx.collect(c);

		if (canBePublic) {
			this.publicDocsCounter.inc();
			if (isShared) {
				this.sharedDocsCounter.inc();
				this.sharedAndAllowed.inc();
			}
		} else {
			this.privateDocsCounter.inc();
			if (isShared) {
				this.sharedButPrivateCounter.inc();
				this.sharedDocsCounter.inc();
			}
		}
	}

	private DocumentSharedEvent randomDocumentSharedEvent(
			Cooperation cooperation,
			boolean isPublic,
			boolean canBePublic
	) {
		String audience = isPublic
				? "public"
				: String.format("%s@%s.com", cooperation.randomEmployee(), cooperation.getName());

		String title = canBePublic ? "lunch-menu" : "finances";

		return new DocumentSharedEvent("GDrive", title, audience, "Spreadsheet");
	}
}
