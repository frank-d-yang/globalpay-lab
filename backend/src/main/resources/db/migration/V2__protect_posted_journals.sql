-- Protect JournalLines from modification after posting

CREATE FUNCTION guard_journal_line_mutation()
    RETURNS TRIGGER AS $$
DECLARE
target_journal_id UUID;
    current_status VARCHAR(16);
BEGIN
    IF TG_OP = 'INSERT' THEN
        target_journal_id := NEW.journal_entry_id;

    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.journal_entry_id IS DISTINCT FROM OLD.journal_entry_id THEN
            RAISE EXCEPTION 'Cannot reassign a journal line';
END IF;

        target_journal_id := OLD.journal_entry_id;

ELSE
        target_journal_id := OLD.journal_entry_id;
END IF;

    -- Lock the parent JournalEntry and read its current status
SELECT status INTO current_status
FROM journal_entry
WHERE id = target_journal_id
    FOR UPDATE;

IF NOT FOUND THEN
        RAISE EXCEPTION 'Journal not found';
END IF;

    IF current_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Cannot modify lines of a posted journal';
END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guard_journal_line
    BEFORE INSERT OR UPDATE OR DELETE
                     ON journal_line
                         FOR EACH ROW
                         EXECUTE FUNCTION guard_journal_line_mutation();

-- Protect posted JournalEntry records

CREATE FUNCTION guard_posted_journal()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'POSTED' THEN
        RAISE EXCEPTION 'Cannot modify or delete a posted journal';
END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guard_posted_journal
    BEFORE UPDATE OR DELETE
ON journal_entry
FOR EACH ROW
EXECUTE FUNCTION guard_posted_journal();
