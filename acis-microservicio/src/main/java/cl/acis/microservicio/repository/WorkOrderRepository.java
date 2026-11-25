package cl.acis.microservicio.repository;

import cl.acis.microservicio.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
}
