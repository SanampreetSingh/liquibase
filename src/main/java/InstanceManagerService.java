import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.xsa.core.instancemanager.client.*;
import com.sap.xsa.core.instancemanager.client.impl.InstanceManagerClientFactory;
import com.sap.xsa.core.instancemanager.client.impl.ManagedServiceInstanceImpl;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class InstanceManagerService {

	String liquibaseChangeLogMaster = "db/changelog/db.changelog-master.yaml";

	public void runLiquibaseOnTenantDatabase(String tenantId) {
		ManagedServiceInstance managedServiceInstance = getManagedService();

		Connection dbConnection = null;
		ServiceAccess serviceAccess = getServiceAccess(managedServiceInstance);
		try {
			dbConnection = serviceAccess.getDBConnection();
//			dbConnection = DriverManager.getConnection(managedServiceInstance.getCredentials().get("url").toString(),
//				managedServiceInstance.getCredentials().get("user").toString(),
//				managedServiceInstance.getCredentials().get("password").toString());
		} catch (Exception ex) {
			// tenants in a bad state may not have a credentials block which causes an NPE so make sure we catch that also.
			System.out.println("runLiquibaseOnTenantDatabase: could not get database connection for tenant: " + managedServiceInstance.getId());
		}

		if (dbConnection == null) {
			System.out.println("runLiquibaseOnTenantDatabase: database connection is null for tenant:" + managedServiceInstance.getId());
		}

		System.out.println(dbConnection);
		Database database = getDatabase(dbConnection, tenantId);

		Liquibase liquibase = getLiquibase(database, tenantId);

		try {
			liquibase.update(new Contexts(), new LabelExpression());
		} catch (LiquibaseException ex) {
			System.out.println("runLiquibaseOnTenantDatabase: failed to update database for tenant: " + tenantId);
			ex.printStackTrace();
		}
	}

	Database getDatabase(Connection dbConnection, String tenantId) {
		try {
			return getDatabaseFactory().findCorrectDatabaseImplementation(new JdbcConnection(dbConnection));
		} catch (DatabaseException ex) {
			String driverName = "Unknown";
			String driverVersion = "Unknown";
			try {
				driverName = dbConnection.getMetaData().getDriverName();
				driverVersion = dbConnection.getMetaData().getDriverVersion();
			} catch (SQLException ex2) {
				System.out.println("createTenantDatabase: could not determine database driver name/version");
			}
			System.out.println("createTenantDatabase: Could not get database for tenant [{}] with driver [{}], version [{}]" + tenantId + driverName + driverVersion);
			return null;
		}
	}

	DatabaseFactory getDatabaseFactory() {
		return DatabaseFactory.getInstance();
	}

	ServiceAccess getServiceAccess(ManagedServiceInstance managedServiceInstance) {
		return InstanceManagerClientFactory.getServiceAccess(managedServiceInstance);
	}

	Liquibase getLiquibase(Database database, String tenantId) {
		try {
			System.out.println("getLiquibase: for tenant [{}] using changelog file [{}]" + tenantId + liquibaseChangeLogMaster);
			return new Liquibase(liquibaseChangeLogMaster, new ClassLoaderResourceAccessor(), database);
		} catch (Exception ex) {
			System.out.println("createTenantDatabase: could not create Liquibase instance for tenant [{}]" + tenantId);
			return null;
		}
	}

	public static void main(String[] args) {
		InstanceManagerService service = new InstanceManagerService();
//		service.runLiquibaseOnTenantDatabase("tenant1");
		service.dbConnectionCheck();
	}

	public void dbConnectionCheck() {
		try {
			ManagedServiceInstance managedServiceInstance = getManagedService();

			Connection conn = DriverManager.getConnection(managedServiceInstance.getCredentials().get("url").toString(),
				managedServiceInstance.getCredentials().get("user").toString(),
				managedServiceInstance.getCredentials().get("password").toString());
			Class.forName("com.sap.db.jdbc.Driver");

			String query = "SELECT * FROM DUMMY";
			PreparedStatement stmt = conn.prepareStatement(query);

			ResultSet rs = stmt.executeQuery();
			rs.next();

			System.out.println("DUMMY value: " + rs.getString(1));
			stmt.close();
			conn.close();
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private ManagedServiceInstance getManagedService() {
		ManagedServiceInstance managedServiceInstance = null;
		try {
			ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			managedServiceInstance = mapper.readValue(new File("src/main/resources/tenantServiceInstance.json"), ManagedServiceInstanceImpl.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return managedServiceInstance;
	}

}
